package server_starter;

import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@ExtendWith(ITCustomServersShould.class)
public class ITCustomServersShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/server_starter/resources/custom-server.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(ITCustomServersShould.class)
    public void start(TesterConnector connector) throws Exception {
        assertArrayEquals(new String[]{ "user1" }, connector.getServerPetition().getPlayers(),
                "Expected to get only 'user1' connected (as specified in the config); got something different instead");
    }
}
