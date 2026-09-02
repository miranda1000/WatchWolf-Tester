package server_starter;

import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import generic.ITTesterTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ITServerStarterExceptionsShould.class)
public class ITServerStarterExceptionsShould extends AbstractTest {
    private final ArrayList<Exception> exceptionsOnBeforeAll = new ArrayList<>();

    @Override
    public String getConfigFile() {
        return "src/integration-test/java/server_starter/resources/non-existent.yaml";
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        try {
            super.beforeAll(extensionContext);
        } catch (Exception ex) {
            ITServerStarterExceptionsShould tis = (ITServerStarterExceptionsShould)AbstractTest.getInstance(ITServerStarterExceptionsShould.class);
            tis.exceptionsOnBeforeAll.add(ex);
        }
    }

    @Test
    public void warnTheUserIfTryingToStartAServerWithAnUnavailableVersion() throws Exception {
        ITServerStarterExceptionsShould tis = (ITServerStarterExceptionsShould)AbstractTest.getInstance(ITServerStarterExceptionsShould.class);
        assertFalse(tis.exceptionsOnBeforeAll.isEmpty(), "Expected to get exception while starting the server; got nothing instead");
        System.out.println("Got exceptions: " + tis.exceptionsOnBeforeAll.toString());
    }
}
