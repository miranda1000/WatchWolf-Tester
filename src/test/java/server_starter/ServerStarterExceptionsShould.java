package server_starter;

import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import generic.TesterTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ServerStarterExceptionsShould.class)
public class ServerStarterExceptionsShould extends AbstractTest {
    private final ArrayList<Exception> exceptionsOnBeforeAll = new ArrayList<>();

    @Override
    public String getConfigFile() {
        return "src/test/java/server_starter/resources/non-existent.yaml";
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        try {
            super.beforeAll(extensionContext);
        } catch (Exception ex) {
            ServerStarterExceptionsShould tis = (ServerStarterExceptionsShould)AbstractTest.getInstance(ServerStarterExceptionsShould.class);
            tis.exceptionsOnBeforeAll.add(ex);
        }
    }

    @Test
    public void warnTheUserIfTryingToStartAServerWithAnUnavailableVersion() throws Exception {
        ServerStarterExceptionsShould tis = (ServerStarterExceptionsShould)AbstractTest.getInstance(ServerStarterExceptionsShould.class);
        assertFalse(tis.exceptionsOnBeforeAll.isEmpty(), "Expected to get exception while starting the server; got nothing instead");
        System.out.println("Got exceptions: " + tis.exceptionsOnBeforeAll.toString());
    }
}
