package server_starter;

import dev.watchwolf.entities.Difficulty;
import dev.watchwolf.entities.ServerType;
import dev.watchwolf.entities.WorldType;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.Tester;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.function.Supplier;

@ExtendWith(ServerStarterShould.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServerStarterShould extends AbstractTest {
    private class ServerStartedInfo {
        public ServerType type;
        public String version;
        public double timestamp;

        public ServerStartedInfo(ServerType type, String version, double timestamp) {
            this.type = type;
            this.version = version;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return this.type.name() + " " + this.version;
        }
    }

    private static final int TIMEOUT = (3+10)*60; // ~3 minutes to queue all the servers, and leave 10 minutes to let them start

    private ArrayList<String> expected;
    double startTime;
    private ArrayList<ServerStartedInfo> got;
    private ArrayList<Tester> serverTesters;

    @Override
    public String getConfigFile() {
        return "src/test/java/server_starter/resources/basic.yaml";
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws IOException {
        // code inspired by AbstractTest#beforeAll
        AbstractTest.addInstance((Class<? extends AbstractTest>) extensionContext.getTestClass().orElseThrow((Supplier<? extends RuntimeException>) () -> {throw new IllegalArgumentException("Extension context not extends of AbstractTest");}), this);

        this.serverTesters = new ArrayList<>();
        this.expected = new ArrayList<>();
        this.got = new ArrayList<>();

        final Object waitForStartup = new Object();
        for (ServerType serverType : this.fileLoader.getServerTypes()) {
            for (String serverVersion : this.fileLoader.getServerVersions(serverType)) {
                this.expected.add(serverType.name() + " " + serverVersion);

                Socket serversManagerSocket = new Socket(this.fileLoader.getProvider(), 8000),
                        clientsManagerSocket = new Socket(this.fileLoader.getProvider(), 7000); // TODO we won't use any user

                System.out.println("Starting server for " + serverType.name() + " " + serverVersion);
                Tester tester = new Tester(serversManagerSocket, serverType, serverVersion, this.fileLoader.getPlugin(),
                        this.fileLoader.getExtraPlugins(), WorldType.FLAT, "1", Difficulty.PEACEFUL, this.fileLoader.getMaps(), this.fileLoader.getConfigFiles(),
                        clientsManagerSocket, this.fileLoader.getUsers(), this.fileLoader.getOverrideSync(),
                        this.fileLoader.getProvider().equals("127.0.0.1") ? Tester.IP_WSL_MODIFY : Tester.IP_NO_MODIFY)
                        /*.setOnServerError(Tester.DEFAULT_ERROR_PRINT)*/;

                this.serverTesters.add(tester);

                tester.setOnServerReady((connector) -> {
                    synchronized (waitForStartup) {
                        this.got.add(new ServerStartedInfo(connector.getServerType(), connector.getServerVersion(), System.currentTimeMillis()));

                        //server.connector = connector; // TODO why is this needed?
                        waitForStartup.notify();
                    }
                });
            }
        }

        this.startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            synchronized (waitForStartup) {
                for (Tester server : this.serverTesters) server.run();

                try {
                    for (int n = 0; n < this.serverTesters.size(); n++) waitForStartup.wait();
                    // at this point the connectors are ready; end the setup and start the tests
                } catch (InterruptedException ignore) {} // timedout; leave the loop
            }
        });
        try {
            future.get(TIMEOUT, TimeUnit.SECONDS); // wait for the servers to start until timeout
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (Exception ignore) {}
          finally {
            executor.shutdownNow();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        for (final Tester server : this.serverTesters) {
            Future<?> future = executor.submit(() -> server.close());
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); // dead connection
            } catch (Exception ignore) {}
        }
        executor.shutdownNow();
    }

    @Test
    @Order(1)
    public void startupSucceeded() throws Exception {
        ServerStarterShould tis = (ServerStarterShould) AbstractTest.getInstance(this.getClass());

        StringBuilder sb;
        if (tis.got.size() > 0) {
            sb = new StringBuilder();
            sb.append("Timings:\n");
            double avgTime = 0, maxTime = 0;
            for (ServerStartedInfo server : tis.got) {
                double time = (server.timestamp - tis.startTime) / 1000;
                System.out.println(server.toString() + ": " + time + "\n");

                avgTime += time;
                if (time > maxTime) maxTime = time;
            }
            sb.append("Statistics: avg " + (avgTime/tis.got.size()) + ", max " + maxTime + "\n\n");
            System.out.println(sb.toString());
        }

        if (tis.expected.size() == tis.got.size()) return; // all ok!

        sb = new StringBuilder();
        sb.append("Expected ").append(tis.expected.size()).append(" servers, got ").append(tis.got.size()).append(".\n\nDifferences:\n");
        for (final String expected : tis.expected) {
            boolean isUp = tis.got.stream().map(server -> server.toString()).anyMatch(server -> server.equals(expected));
            if (!isUp) sb.append("- ").append(expected).append("\n");
        }
        throw new RuntimeException(sb.toString());
    }

    @Test
    @Order(2)
    public void testConnection() throws Exception {
        ServerStarterShould tis = (ServerStarterShould) AbstractTest.getInstance(this.getClass());

        final ArrayList<String> timedout = new ArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        for (final Tester t : tis.serverTesters) {
            // try to ask to sync the server; if failed, append to `timedout`
            Future<?> future = executor.submit(() -> {
                try {
                    t.getConnector().server.synchronize();
                } catch (IOException e) {
                    timedout.add(t.getConnector().getServerType().name() + " " + t.getConnector().getServerVersion());
                }
            });
            try {
                future.get(5, TimeUnit.SECONDS); // 5s to get the response
            } catch (TimeoutException e) {
                future.cancel(true);
                timedout.add(t.getConnector().getServerType().name() + " " + t.getConnector().getServerVersion());
            } catch (Exception ignore) {}
        }
        executor.shutdownNow();

        if (timedout.size() == 0) return; // all ok

        StringBuilder sb = new StringBuilder();
        sb.append("This servers didn't keep their connection opened: ");
        for (String server : timedout) sb.append(server).append(", ");
        sb.setLength(sb.length()-2); // remove last ', '
        throw new RuntimeException(sb.toString());
    }
}
