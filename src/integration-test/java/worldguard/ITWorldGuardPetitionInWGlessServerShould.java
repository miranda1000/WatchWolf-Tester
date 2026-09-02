package worldguard;

import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(ITWorldGuardPetitionInWGlessServerShould.class)
public class ITWorldGuardPetitionInWGlessServerShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    /**
     * The generic config file doesn't contain WG dependency, so using WG petitions
     * in that server should raise an error (but it must keep alive the WW service)
     */
    @ParameterizedTest
    @ArgumentsSource(ITWorldGuardPetitionInWGlessServerShould.class)
    public void processRequestWithoutCrashing(TesterConnector connector) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final AtomicReference<Exception> ex = new AtomicReference<>(null);

        // run the WG petition
        Future<?> future = executor.submit(() -> {
            try {
                connector.getRegions();
            } catch (IOException e) {
                ex.set(e);
            }
        });
        if (ex.get() != null) throw ex.get();
        try {
            future.get(5, TimeUnit.SECONDS); // 5s to get the response
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }

        // is the server alive?
        future = executor.submit(() -> {
            try {
                connector.server.synchronize();
            } catch (IOException e) {
                ex.set(e);
            }
        });
        if (ex.get() != null) throw ex.get();
        try {
            future.get(5, TimeUnit.SECONDS); // 5s to get the response
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }
}
