package dev.watchwolf.tester;

import dev.watchwolf.entities.Difficulty;
import dev.watchwolf.entities.SocketHelper;
import dev.watchwolf.entities.WorldType;
import dev.watchwolf.entities.files.ConfigFile;
import dev.watchwolf.entities.files.Plugin;
import dev.watchwolf.entities.files.UsualPlugin;
import dev.watchwolf.entities.files.WorldFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `Tester.onServerStart` used to catch its own `IOException`, print it, and then run the test body
 * anyway. The user's first sight of the failure was an `ArrayIndexOutOfBoundsException` on
 * `getClients()[0]`, several frames away from the connection that never opened.
 */
public class TesterSetupFailureShould {
    /** header the ServersManager answers `startServer` with */
    private static final int SERVER_STARTED_RESPONSE = 0b000000000001_1_000;

    private ServerSocket serversManager;
    private ServerSocket clientsManager;
    private Socket serversManagerConnection;
    private Socket clientsManagerConnection;
    private Tester tester;

    /**
     * A ServersManager that hands back <address> and then says nothing more.
     */
    private Tester testerAnsweredWith(String address) throws IOException {
        this.serversManager = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        this.clientsManager = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());

        Thread manager = new Thread(() -> {
            try {
                Socket accepted = this.serversManager.accept();
                ArrayList<Byte> response = new ArrayList<>();
                SocketHelper.addShort(response, SERVER_STARTED_RESPONSE);
                SocketHelper.addString(response, address);
                new DataOutputStream(accepted.getOutputStream()).write(SocketHelper.toByteArray(response));
            } catch (IOException ignore) {}
        });
        manager.setDaemon(true);
        manager.start();

        this.serversManagerConnection = new Socket();
        this.serversManagerConnection.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), this.serversManager.getLocalPort()), 5000);
        this.clientsManagerConnection = new Socket();
        this.clientsManagerConnection.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), this.clientsManager.getLocalPort()), 5000);

        return new Tester(this.serversManagerConnection, "Spigot", "1.16.5", new UsualPlugin("WatchWolf"),
                new Plugin[0], WorldType.FLAT, "1", Difficulty.PEACEFUL, new WorldFile[0], new ConfigFile[0],
                this.clientsManagerConnection, new String[]{"Steve"}, false);
    }

    /**
     * @return A port nothing is listening on
     */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @AfterEach
    public void closeSockets() throws IOException {
        for (java.io.Closeable closeable : new java.io.Closeable[]{this.serversManagerConnection, this.clientsManagerConnection, this.serversManager, this.clientsManager}) {
            if (closeable != null) closeable.close();
        }
    }

    @Test
    @Timeout(60)
    public void handOverTheFailureInsteadOfRunningTheTest() throws IOException {
        // the ServersManager reports an address nothing answers on -- exactly the reporter's case
        int unreachablePort = closedPort();
        this.tester = this.testerAnsweredWith("127.0.0.1:" + (unreachablePort - 1));

        AtomicBoolean testBodyRan = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        this.tester.setOnServerReady((connector) -> testBodyRan.set(true))
                .setOnSetupFailure(failure::set);

        this.tester.run();
        this.tester.onServerStart(); // the ServersManager said the server is up

        assertFalse(testBodyRan.get(), "the test body must not run against a half-built connector");
        assertNotNull(failure.get(), "the setup failure must be handed over, not printed and forgotten");
        assertInstanceOf(ServerSetupException.class, failure.get());
        assertTrue(failure.get().getMessage().contains("127.0.0.1:" + unreachablePort),
                "the failure must name the address it could not reach: " + failure.get().getMessage());
        assertTrue(failure.get().getMessage().contains("provider"),
                "the failure must say how to point the Tester somewhere reachable: " + failure.get().getMessage());
    }

    @Test
    @Timeout(60)
    public void buildTheEnvironmentOnlyOnce() throws IOException {
        // "server started" reaches us from both the async loop and the synchronous request loops
        this.tester = this.testerAnsweredWith("127.0.0.1:" + (closedPort() - 1));

        AtomicInteger failures = new AtomicInteger(0);
        this.tester.setOnSetupFailure((ex) -> failures.incrementAndGet());

        this.tester.run();
        this.tester.onServerStart();
        this.tester.onServerStart();

        assertEquals(1, failures.get(), "the setup must not run again for a repeated 'server up'");
    }

    @Test
    @Timeout(60)
    public void nameTheServerThatCouldNotStart() throws IOException {
        this.tester = this.testerAnsweredWith(""); // the ServersManager refused

        ServerSetupException ex = assertThrows(ServerSetupException.class, () -> this.tester.run());

        assertEquals("Spigot", ex.getServerType());
        assertEquals("1.16.5", ex.getServerVersion());
        assertTrue(ex.getMessage().contains("Spigot") && ex.getMessage().contains("1.16.5"), ex.getMessage());
    }
}
