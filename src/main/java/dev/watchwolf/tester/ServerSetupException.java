package dev.watchwolf.tester;

/**
 * A failure that happened while WatchWolf was still building the environment, before a single test
 * body ran.
 *
 * Setup failures used to be printed and swallowed, so the test ran anyway against a half-built
 * connector and the user saw the damage instead of the cause -- typically an
 * `ArrayIndexOutOfBoundsException` on `getClients()[0]`. Throwing this from `beforeAll` keeps the
 * report pointing at the phase that actually failed.
 */
public class ServerSetupException extends RuntimeException {
    private final String serverType;
    private final String serverVersion;

    public ServerSetupException(String serverType, String serverVersion, String message) {
        this(serverType, serverVersion, message, null);
    }

    public ServerSetupException(String serverType, String serverVersion, String message, Throwable cause) {
        super(describe(serverType, serverVersion) + message, cause);
        this.serverType = serverType;
        this.serverVersion = serverVersion;
    }

    private static String describe(String serverType, String serverVersion) {
        if (serverType == null && serverVersion == null) return "";
        return "[" + serverType + " " + serverVersion + "] ";
    }

    public String getServerType() {
        return this.serverType;
    }

    public String getServerVersion() {
        return this.serverVersion;
    }
}
