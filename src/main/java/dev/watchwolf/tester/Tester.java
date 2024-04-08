package dev.watchwolf.tester;

import dev.watchwolf.entities.*;
import dev.watchwolf.entities.ServerType;
import dev.watchwolf.entities.files.ConfigFile;
import dev.watchwolf.entities.files.Plugin;
import dev.watchwolf.entities.files.WorldFile;
import dev.watchwolf.serversmanager.ServerErrorNotifier;
import dev.watchwolf.serversmanager.ServerStartNotifier;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Tester implements Runnable, ServerStartNotifier {
    public static final ServerErrorNotifier DEFAULT_ERROR_PRINT = (err) -> System.err.println("-- Server error --\n" + err.replaceAll("\\\\n", System.lineSeparator()).replaceAll("\\\\t", "\t"));

    public static final IPModifier IP_NO_MODIFY = (ip)->ip,
                                    IP_WSL_MODIFY = (ip)-> {
                                        try {
                                            return InetAddress.getLocalHost().getHostAddress();
                                        } catch (UnknownHostException e) {
                                            return "127.0.0.1";
                                        }
                                    };

    private TesterConnector connector;
    private String serverIp;
    private int serverPort, serverSocketPort;

    private Runnable onServerReady;
    private ServerErrorNotifier onError;
    private final String mcType;
    private final String version;
    private final Plugin testedPlugin;
    private final Plugin[] extraPlugins;
    private final WorldType worldType;
    private final String seed;
    private final WorldFile[] maps;
    private final ConfigFile[] configFiles;
    private final String[] clientNames;

    private final IPModifier ipModifier;

    private final Difficulty initialDifficulty;

    public Tester(Socket serverManagerSocket, String mcType, String version, Plugin testedPlugin, Plugin[] extraPlugins, WorldType worldType, String seed, Difficulty difficulty, WorldFile[] maps, ConfigFile[] configFiles, Socket clientsManagerSocket, String[] clientNames, boolean overrideSync, IPModifier ipModifier) {
        this.connector = new TesterConnector(serverManagerSocket, clientsManagerSocket, overrideSync);

        this.mcType = mcType;
        this.version = version;
        this.testedPlugin = testedPlugin;
        this.extraPlugins = extraPlugins;
        this.worldType = worldType;
        this.seed = seed;
        this.maps = maps;
        this.configFiles = configFiles;
        this.clientNames = clientNames;
        this.ipModifier = ipModifier;
        this.initialDifficulty = difficulty;
    }

    public Tester(Socket serverManagerSocket, String mcType, String version, Plugin testedPlugin, Plugin[] extraPlugins, WorldType worldType, String seed, Difficulty difficulty, WorldFile[] maps, ConfigFile[] configFiles, Socket clientsManagerSocket, String[] clientNames, boolean overrideSync) {
        this(serverManagerSocket, mcType, version, testedPlugin, extraPlugins, worldType, seed, difficulty, maps, configFiles, clientsManagerSocket, clientNames, overrideSync, Tester.IP_NO_MODIFY);
    }

    public Tester setOnServerReady(ServerStartNotifier onServerReady) {
        this.onServerReady = onServerReady::onServerStart;
        return this;
    }

    public Tester setOnServerReady(Consumer<TesterConnector> onServerReady) {
        this.onServerReady = ()->onServerReady.accept(this.getConnector());
        return this;
    }

    public Tester setOnServerError(ServerErrorNotifier onError) {
        this.onError = onError;
        return this;
    }

    @Override
    public void run() {
        try {
            List<Plugin> serverPlugins = new ArrayList<>();
            Collections.addAll(serverPlugins, this.extraPlugins);
            serverPlugins.add(testedPlugin);
            String ipPlusPort = this.connector.startServer(this, this.onError, this.mcType, this.version,
                    serverPlugins.toArray(new Plugin[0]), this.worldType, this.seed, this.maps, this.configFiles);
            if (ipPlusPort.equals("")) throw new IOException("Failed to start server " + this.mcType + " version " + this.version);
            String []ip = ipPlusPort.split(":");
            new Thread(this.connector).start();

            this.serverIp = this.ipModifier.modifyIp(ip[0]);
            this.serverPort = Integer.parseInt(ip[1]);
            this.serverSocketPort = this.serverPort + 1; // the server socket port it's the next of the server port

            System.out.println("Server started (" + this.serverIp + ":" + this.serverPort + "), waiting for the 'server up' message");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onServerStart() {
        // connect to the server socket
        try {
            System.out.println("Connecting to " + this.serverIp + ":" + this.serverSocketPort + " (server)...");
            this.connector.setServerManagerSocket(new Socket(this.ipModifier.modifyIp(this.serverIp), this.serverSocketPort), this.mcType, this.version);

            // set the difficulty
            this.connector.setDifficulty(this.initialDifficulty);

            // whitelist the players
            for (String client : this.clientNames) this.connector.whitelistPlayer(client);
            this.connector.synchronize(); // synchronize with the server (don't connect before the dispatcher whitelist the player!)

            // start the clients
            for (String client : this.clientNames) {
                String ip = this.connector.startClient(client, this.serverIp + ":" + this.serverPort);
                if (ip.length() == 0) throw new IOException("Cannot start client");
                System.out.println("Connecting to " + ip + " (client " + client + ")...");
                String []clientIp = ip.split(":");
                this.connector.setClientSocket(new Socket(this.ipModifier.modifyIp(clientIp[0]), Integer.parseInt(clientIp[1])), client);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // start the test
        if (this.onServerReady != null) this.onServerReady.run();
    }

    public void close() {
        // try to close the server
        try {
            this.connector.stopServer(null);
        } catch (IOException ignore) {}

        // close the connections
        this.connector.close();
        this.connector = null;
    }

    public TesterConnector getConnector() {
        return this.connector;
    }
}
