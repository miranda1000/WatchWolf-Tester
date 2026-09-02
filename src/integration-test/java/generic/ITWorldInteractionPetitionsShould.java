package generic;

import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.blocks.Blocks;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.ExtendedClientPetition;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ITWorldInteractionPetitionsShould.class)
public class ITWorldInteractionPetitionsShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(ITWorldInteractionPetitionsShould.class)
    public void syncBeforeAction(TesterConnector connector) throws Exception {
        String username = connector.getClients()[0];
        ExtendedClientPetition client = connector.getClientPetition(username);
        Position []tests = new Position[5];
        tests[0] = client.getPosition();
        tests[1] = new Position("world", 1000, tests[0].getY(), 1000);
        tests[2] = new Position("world", 1000, tests[0].getY(), 1500);
        tests[3] = new Position("world", -1000, tests[0].getY(), 1000);
        tests[4] = new Position("world", 10000, tests[0].getY(), 10000);

        for (Position test : tests) {
            Position toBreak = test.add(0,-1,0);
            connector.server.tp(username, test);
            client.breakBlock(toBreak);
            assertEquals(Blocks.AIR, connector.server.getBlock(toBreak), "Failed while breaking block at " + toBreak.toString());
        }
    }
}
