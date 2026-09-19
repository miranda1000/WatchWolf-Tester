package dev.watchwolf.tester;

import dev.watchwolf.entities.ServerType;
import dev.watchwolf.entities.files.ConfigFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class AbstractTest implements TestWatcher, // send feedback
        BeforeAllCallback, AfterAllCallback, // open/close server
        ArgumentsProvider { // send arguments
    private static class ServerInstance {
        public Tester tester;
        public TesterConnector connector;
        public Throwable setupFailure;
        public String serverType;
        public String serverVersion;

        public boolean isPending() {
            return this.connector == null && this.setupFailure == null;
        }

        public String describe() {
            return this.serverType + " " + this.serverVersion;
        }
    }

    private static HashMap<Class<? extends AbstractTest>, AbstractTest> instances = new HashMap<>();

    private ArrayList<ServerInstance> servers;
    private HashMap<ServerInstance,HashMap<String,Integer>> cameras;

    private UUID testID;

    protected TestConfigFileLoader fileLoader;

    public AbstractTest() throws ConfigFileException {
        try {
            this.fileLoader = new TestConfigFileLoader(this.getConfigFile());
        } catch (IOException ex) {
            throw new ConfigFileException(ex);
        }
    }

    protected static void addInstance(Class<? extends AbstractTest> cls, AbstractTest instance) {
        AbstractTest.instances.put(cls, instance);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws IOException {
        AbstractTest.addInstance((Class<? extends AbstractTest>) extensionContext.getTestClass().orElseThrow((Supplier<? extends RuntimeException>) () -> {throw new IllegalArgumentException("Extension context not extends of AbstractTest");}), this);

        this.servers = new ArrayList<>();
        this.testID = UUID.randomUUID();

        final Object waitForStartup = new Object();
        try {
            this.buildTesters(waitForStartup);
        } catch (Throwable ex) {
            this.closeEveryTester(); // don't leave the sockets we did open behind
            throw ex;
        }

        long timeoutMillis = this.fileLoader.getStartupTimeout() * 1000L;
        synchronized (waitForStartup) {
            for (ServerInstance server : this.servers) {
                try {
                    server.tester.run();
                } catch (Throwable ex) {
                    // never leave a server pending forever because the one before it failed to ask
                    server.setupFailure = ex;
                }
            }

            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (this.servers.stream().anyMatch(ServerInstance::isPending)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break; // reported below, naming who never came up
                try {
                    waitForStartup.wait(remaining);
                } catch (InterruptedException ignore) {
                    break;
                }
            }
        }

        this.failIfAnyServerIsNotReady(timeoutMillis);
        // at this point the connectors are ready; end the setup and start the tests
    }

    /**
     * One Tester per server type x version, each with its own pair of manager connections.
     */
    private void buildTesters(final Object waitForStartup) throws IOException {
        for (String serverType : this.fileLoader.getServerTypes()) {
            for (String serverVersion : this.fileLoader.getServerVersions(serverType)) {
                final ServerInstance server = new ServerInstance();
                server.serverType = serverType;
                server.serverVersion = serverVersion;
                this.servers.add(server);

                Socket serversManagerSocket = this.connectToManager("ServersManager", 8000);
                Socket clientsManagerSocket;
                try {
                    clientsManagerSocket = this.connectToManager("ClientsManager", 7000);
                } catch (RuntimeException ex) {
                    // no Tester owns this one yet, so nothing else would ever close it
                    try {
                        serversManagerSocket.close();
                    } catch (IOException ignore) {}
                    throw ex;
                }

                System.out.println("Starting server for " + serverType + " " + serverVersion + " using ID " + testID.toString());
                server.tester = new Tester(serversManagerSocket, serverType, serverVersion, this.fileLoader.getPlugin(),
                        this.fileLoader.getExtraPlugins(), this.fileLoader.getWorldType(), this.fileLoader.getSeed(), this.fileLoader.getDifficulty(),
                        this.fileLoader.getMaps(), this.fileLoader.getConfigFiles(),
                        clientsManagerSocket, this.fileLoader.getUsers(), this.fileLoader.getOverrideSync(),
                        this.fileLoader.getProvider().equals("127.0.0.1") ? Tester.IP_WSL_MODIFY : Tester.IP_NO_MODIFY)
                                .setOnServerError(Tester.DEFAULT_ERROR_PRINT); // TODO report to JUnit

                server.tester.setOnServerReady((connector) -> {
                    // @pre This needs to go before notifying
                    try {
                        if (this.fileLoader.reportTimings()) connector.server.startTimings();
                        connector.server.setInvincibleMode(this.fileLoader.getInvincibleModeEnabled());

                        this.beforeAll(connector);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    synchronized (waitForStartup) {
                        server.connector = connector;
                        waitForStartup.notifyAll();
                    }
                });

                // a setup failure happens on the connector's async thread; bring it back here, where
                // it can still stop the tests from running
                server.tester.setOnSetupFailure((ex) -> {
                    synchronized (waitForStartup) {
                        server.setupFailure = ex;
                        waitForStartup.notifyAll();
                    }
                });
            }
        }
    }

    /**
     * Opens a socket to one of the managers, bounded, and says which one did not answer.
     */
    private Socket connectToManager(String manager, int port) throws ServerSetupException {
        String provider = this.fileLoader.getProvider();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(provider, port), Tester.CONNECT_TIMEOUT);
            return socket;
        } catch (IOException ex) {
            try {
                socket.close();
            } catch (IOException ignore) {}

            throw new ServerSetupException(null, null,
                    "could not reach the " + manager + " at " + provider + ":" + port + " (" + ex.getMessage() + "). "
                    + "Is the WatchWolf environment running, and is `provider` in your config file the host it runs on?", ex);
        }
    }

    /**
     * Turns whatever went wrong during the setup into a single failure of `beforeAll`, so no test
     * body ever runs against a half-built connector.
     */
    private void failIfAnyServerIsNotReady(long timeoutMillis) {
        List<ServerInstance> broken = new ArrayList<>();
        for (ServerInstance server : this.servers) {
            if (server.connector == null) broken.add(server);
        }
        if (broken.isEmpty()) return;

        StringBuilder report = new StringBuilder("WatchWolf could not set up ")
                .append(broken.size()).append(" of ").append(this.servers.size())
                .append(" server(s); no test was run:");
        for (ServerInstance server : broken) {
            report.append(System.lineSeparator()).append("  - ").append(server.describe()).append(": ");
            if (server.setupFailure != null) report.append(server.setupFailure.getMessage());
            else report.append("never became ready (gave up after ").append(timeoutMillis / 1000L)
                    .append("s). The server was asked for, but the 'server up' message never arrived -- "
                            + "see the ServersManager log for what the Minecraft server did.");
        }

        this.closeEveryTester(); // whatever did come up is ours to clean up; the test will not do it

        Throwable firstCause = broken.stream().map(server -> server.setupFailure)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        throw new ServerSetupException(null, null, report.toString(), firstCause);
    }

    private void closeEveryTester() {
        if (this.servers == null) return;

        for (ServerInstance server : this.servers) {
            if (server.tester == null) continue;
            try {
                server.tester.close();
            } catch (Throwable ignore) {}
        }
    }

    /**
     * This method will run just once (for each server), at the start. Use it to setup all the tests (e.g. position
     * one player to a specific place, or giving him items)
     * @param server The connector to the server that is being enabled right now
     */
    public void beforeAll(TesterConnector server) throws IOException {}

    @Override
    public void afterAll(ExtensionContext extensionContext) throws IOException {
        if (this.servers == null) return; // the setup never got far enough to start anything

        for (ServerInstance server : this.servers) {
            TesterConnector connector = server.connector;
            if (connector == null) continue; // this one never came up; `beforeAll` already closed it

            this.afterAll(connector); // let the test close before the server actually stops

            if (this.fileLoader.reportTimings()) {
                System.out.println("Getting timings report... DO NOT STOP the tests.");
                connector.server.stopTimings()
                        .saveToFile(
                                new File(this.fileLoader.getTimingsDirectory(),
                                        "timings-" + connector.getServerType() + "-"
                                                + connector.getServerVersion() + ".html")
                        );
            }

            server.tester.close();
        }

        // TODO send 'done' to website
    }

    /**
     * This method will run just once (for each server), at the end. Use it to close resources from all the tests
     * @param server The connector to the server that is being enabled right now
     */
    public void afterAll(TesterConnector server) throws IOException {}

    @Override
    public void testSuccessful(ExtensionContext context) {
        // TODO send 'ok' to website
        System.err.println("Test " + context.getDisplayName() + " succeed");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        // TODO send 'fail' to website
        System.err.println("Test " + context.getDisplayName() + " failed: " + cause.getMessage());
    }

    @BeforeEach
    public void startRecording() throws IOException {
        AbstractTest tis = AbstractTest.getInstance(this.getClass());
        if (tis.fileLoader.getRecordingsDirectory() == null) return; // don't record

        tis.cameras = new HashMap<>();
        for (ServerInstance si : tis.servers) {
            HashMap<String,Integer> serverCameras = new HashMap<>();

            for (String player : si.connector.server.getPlayers()) {
                int playerCameraId = si.connector.getClientPetition(player)
                                .start_recording();
                serverCameras.put(player, playerCameraId);
            }

            tis.cameras.put(si, serverCameras);
        }
    }

    @AfterEach
    public void doneRecording(TestInfo testInfo) throws IOException {
        AbstractTest tis = AbstractTest.getInstance(this.getClass());
        if (tis.cameras == null) return;

        for (Map.Entry<ServerInstance,HashMap<String,Integer>> server : tis.cameras.entrySet()) {
            TesterConnector connector = server.getKey().connector;
            File recordingsFolder = new File(new File(tis.fileLoader.getRecordingsDirectory(), testInfo.getTestMethod().get().getName()),
                    connector.getServerType() + "-" + connector.getServerVersion());

            for (Map.Entry<String,Integer> user : server.getValue().entrySet()) {
                String username = user.getKey();
                connector.getClientPetition(username).stop_recording(user.getValue(), new File(recordingsFolder, username + ".mp4"));
            }
        }
    }

    protected static AbstractTest getInstance(Class<?> cls) throws IllegalArgumentException {
        AbstractTest instance = AbstractTest.instances.get(cls);
        if (instance == null) throw new IllegalArgumentException("Instance of " + cls + " not instantiated.");
        return instance;
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
        return AbstractTest.getInstance(extensionContext.getTestClass().orElse(null)).servers.stream().map(e -> Arguments.of(e.connector));
    }

    /*@Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == TesterConnector.class;
    }*/

    /**
     * Method to override
     * @return WatchWolf config file
     */
    public String getConfigFile() { throw new UnspecifiedConfigFileException(); }
}
