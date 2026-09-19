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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Tester implements Runnable, ServerStartNotifier {
    public static final ServerErrorNotifier DEFAULT_ERROR_PRINT = (err) -> System.err.println("-- Server error --\n" + err.replaceAll("\\\\n", System.lineSeparator()).replaceAll("\\\\t", "\t"));

    /**
     * How long to wait for a socket to accept us before saying so.
     * A connection that is going to succeed does so in milliseconds; anything longer is a
     * misconfigured address, and hanging on it only hides that.
     */
    public static final int CONNECT_TIMEOUT = 15_000;

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
    private Consumer<Throwable> onSetupFailure;

    /**
     * "server started" reaches us from both the async poll loop and the synchronous request loops,
     * so it can arrive twice for one server. Building the environment a second time would connect a
     * second socket, start the bots again, and -- now that failures are no longer swallowed -- could
     * report a spurious one after the tests had already been handed a working connector.
     */
    private boolean environmentBuilt = false;
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
        this.connector.setExpectedClients(clientNames);

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

    /**
     * Called when the environment could not be built. `onServerStart` runs on the connector's async
     * thread, so a failure there cannot be thrown to whoever asked for the server -- it has to be
     * handed over.
     * @param onSetupFailure What to do with the failure
     */
    public Tester setOnSetupFailure(Consumer<Throwable> onSetupFailure) {
        this.onSetupFailure = onSetupFailure;
        return this;
    }

    public Tester setOnServerError(ServerErrorNotifier onError) {
        this.onError = onError;
        return this;
    }

    public String getServerType() {
        return this.mcType;
    }

    public String getServerVersion() {
        return this.version;
    }

    @Override
    public void run() {
        try {
            List<Plugin> serverPlugins = new ArrayList<>();
            Collections.addAll(serverPlugins, this.extraPlugins);
            serverPlugins.add(testedPlugin);

            System.out.println("[" + this.mcType + " " + this.version + "] Asking the ServersManager for a server...");
            String ipPlusPort = this.connector.startServer(this, this.onError, this.mcType, this.version,
                    serverPlugins.toArray(new Plugin[0]), this.worldType, this.seed, this.maps, this.configFiles);
            if (ipPlusPort.equals("")) {
                throw new ServerSetupException(this.mcType, this.version,
                        "the ServersManager refused to start this server. It usually means there is no "
                        + this.mcType + "/" + this.version + ".jar in its `server-types/` folder; check the ServersManager log for the reason it reported.");
            }

            String []ip = ipPlusPort.split(":");
            if (ip.length != 2) {
                throw new ServerSetupException(this.mcType, this.version,
                        "the ServersManager answered with '" + ipPlusPort + "', which is not an <ip>:<port> address.");
            }
            new Thread(this.connector).start();

            this.serverIp = this.ipModifier.modifyIp(ip[0]);
            this.serverPort = Integer.parseInt(ip[1]);
            this.serverSocketPort = this.serverPort + 1; // the server socket port it's the next of the server port

            System.out.println("[" + this.mcType + " " + this.version + "] ServersManager reported " + ipPlusPort
                    + (this.serverIp.equals(ip[0]) ? "" : " (rewritten to " + this.serverIp + ":" + this.serverPort + ")")
                    + "; waiting for the 'server up' message");
        } catch (ServerSetupException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ServerSetupException(this.mcType, this.version, "could not ask the ServersManager for a server.", ex);
        }
    }

    @Override
    public void onServerStart() {
        synchronized (this) {
            if (this.environmentBuilt) {
                System.out.println("[" + this.mcType + " " + this.version + "] Got a second 'server up' message; ignoring it.");
                return;
            }
            this.environmentBuilt = true;
        }

        try {
            this.setupEnvironment();
        } catch (Throwable ex) {
            // do NOT run the test body against a half-built connector: that is what turns a setup
            // failure into an unexplainable error inside somebody else's test
            this.reportSetupFailure(ex);
            return;
        }

        // start the test
        try {
            if (this.onServerReady != null) this.onServerReady.run();
        } catch (Throwable ex) {
            this.reportSetupFailure(ex);
        }
    }

    /**
     * Connects to the server that just came up and brings the bots in.
     * Every step names itself and the address it used, so a failure says which phase broke.
     */
    private void setupEnvironment() throws IOException {
        String serverSocket = this.serverIp + ":" + this.serverSocketPort;

        System.out.println("[" + this.mcType + " " + this.version + "] Connecting to " + serverSocket + " (WatchWolf server socket)...");
        this.connector.setServerManagerSocket(this.connect(this.ipModifier.modifyIp(this.serverIp), this.serverSocketPort,
                "the WatchWolf server socket"), this.mcType, this.version);

        System.out.println("[" + this.mcType + " " + this.version + "] Setting the difficulty to " + this.initialDifficulty + "...");
        this.inPhase("set the initial difficulty (" + this.initialDifficulty + ")", () -> this.connector.setDifficulty(this.initialDifficulty));

        System.out.println("[" + this.mcType + " " + this.version + "] Whitelisting " + this.clientNames.length + " user(s)...");
        for (String client : this.clientNames) {
            this.inPhase("whitelist '" + client + "'", () -> this.connector.whitelistPlayer(client));
        }
        // synchronize with the server (don't connect before the dispatcher whitelists the player!)
        this.inPhase("synchronise with the server after whitelisting", () -> this.connector.synchronize());

        int clientNumber = 0;
        for (String client : this.clientNames) {
            clientNumber++;
            System.out.println("[" + this.mcType + " " + this.version + "] Starting client " + clientNumber + "/" + this.clientNames.length + " ('" + client + "')...");

            String ip;
            try {
                ip = this.connector.startClient(client, this.serverIp + ":" + this.serverPort);
            } catch (IOException | RuntimeException ex) {
                throw new ServerSetupException(this.mcType, this.version,
                        "failed to ask the ClientsManager for '" + client + "' (" + ex.getMessage() + ").", ex);
            }
            if (ip.length() == 0) {
                throw new ServerSetupException(this.mcType, this.version,
                        "the ClientsManager could not start '" + client + "' on " + this.serverIp + ":" + this.serverPort
                        + ". The bot never joined -- check the ClientsManager log.");
            }

            String []clientIp = ip.split(":");
            if (clientIp.length != 2) {
                throw new ServerSetupException(this.mcType, this.version,
                        "the ClientsManager answered with '" + ip + "' for '" + client + "', which is not an <ip>:<port> address.");
            }

            System.out.println("[" + this.mcType + " " + this.version + "] Connecting to " + ip + " (client '" + client + "')...");
            this.connector.setClientSocket(this.connect(this.ipModifier.modifyIp(clientIp[0]), Integer.parseInt(clientIp[1]),
                    "client '" + client + "'"), client);
        }
    }

    /**
     * Opens a socket within {@link #CONNECT_TIMEOUT}, saying what we were trying to reach when it
     * does not answer.
     */
    private Socket connect(String host, int port, String what) throws ServerSetupException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            return socket;
        } catch (IOException ex) {
            try {
                socket.close();
            } catch (IOException ignore) {}

            throw new ServerSetupException(this.mcType, this.version,
                    "could not reach " + what + " at " + host + ":" + port + " (" + ex.getMessage() + "). "
                    + "The ServersManager reported this address; if it is not reachable from this machine, "
                    + "set `provider` in your WatchWolf config file to a host this machine can route to.", ex);
        }
    }

    private interface SetupStep {
        void run() throws IOException;
    }

    /**
     * Runs one setup step, and names it if it fails. "Connection reset" on its own says nothing;
     * "failed to whitelist 'Steve' (Connection reset)" says where to look.
     */
    private void inPhase(String what, SetupStep step) throws ServerSetupException {
        try {
            step.run();
        } catch (IOException | RuntimeException ex) {
            throw new ServerSetupException(this.mcType, this.version, "failed to " + what + " (" + ex.getMessage() + ").", ex);
        }
    }

    private void reportSetupFailure(Throwable ex) {
        if (this.onSetupFailure != null) this.onSetupFailure.accept(ex);
        else {
            System.err.println("Setup of " + this.mcType + " " + this.version + " failed, and nobody is listening for it:");
            ex.printStackTrace();
        }
    }

    public void close() {
        if (this.connector == null) return; // already closed

        // try to close the server; a half-built connector must still be closable
        try {
            this.connector.stopServer(null);
        } catch (Exception ignore) {}

        // close the connections
        try {
            this.connector.close();
        } catch (Exception ignore) {}
        this.connector = null;
    }

    public TesterConnector getConnector() {
        return this.connector;
    }
}
