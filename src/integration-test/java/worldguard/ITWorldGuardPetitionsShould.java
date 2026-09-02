package worldguard;

import dev.watchwolf.entities.Position;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ITWorldGuardPetitionsShould.class)
public class ITWorldGuardPetitionsShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/worldguard/resources/config.yaml";
    }

    /**
     * Create a 2x2 region
     */
    @ParameterizedTest
    @ArgumentsSource(ITWorldGuardPetitionsShould.class)
    public void createARegion(TesterConnector connector) throws Exception {
        Position loc1 = new Position("world", 0,0,0),
                loc2 = new Position("world",1,1,0);
        String regionName = "test";

        connector.createRegion(regionName, loc1, loc2);

        List<String> regions = Arrays.asList(connector.getRegions());
        assertTrue(regions.contains(regionName), "Expected new region (" + regionName + ") to be created, got " + regions.toString() + " instead");
    }


    @ParameterizedTest
    @ArgumentsSource(ITWorldGuardPetitionsShould.class)
    public void listRegionsInBlock(TesterConnector connector) throws Exception {
        Position loc1 = new Position("world", 0,50,0),
                loc2 = new Position("world",1,51,0);
        String regionName = "test2";
        Position secondLoc1 = new Position("world", 0,50,10),
                secondLoc2 = new Position("world",1,51,10);
        String secondRegionName = "testNA";

        connector.createRegion(regionName, loc1, loc2);
        connector.createRegion(secondRegionName, secondLoc1, secondLoc2); // this sould be hidden

        List<String> regions = Arrays.asList(connector.getRegions(loc1));
        assertTrue(regions.contains(regionName), "Expected new region (" + regionName + ") to be listed, got " + regions.toString() + " instead");
    }
}
