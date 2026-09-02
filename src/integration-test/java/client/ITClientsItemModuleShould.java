package client;

import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.blocks.Block;
import dev.watchwolf.entities.blocks.Blocks;
import dev.watchwolf.entities.items.Item;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.ExtendedClientPetition;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ITClientsItemModuleShould.class)
public class ITClientsItemModuleShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/client/resources/config.yaml";
    }

    /**
     * Mineflayer change some item names across versions, making WatchWolf Client impossible to find
     * one item in the inventory to be placed. This test will make sure the client can find one of those
     * blocks that changed its name.
     */
    @ParameterizedTest
    @ArgumentsSource(ITClientsItemModuleShould.class)
    public void placeBlockThatChangedItsNameAcrossVersions(TesterConnector connector) throws Exception {
        Thread.sleep(5_000); // TODO sync issues? (eg. start getting data before the client has spawned?)

        String username = connector.getClients()[0];
        ExtendedClientPetition client = connector.getClientPetition(username);
        Position placeAt = client.getPosition().add(1,0,0); // place in front
        Block placeMaterial = Blocks.GRASS_BLOCK;
        Item placeItem = new Item(placeMaterial.getItemType()),
                fillItem = new Item(Blocks.STONE.getItemType());

        // we don't want the item to be placed in the first slot, so we'll fill this
        connector.server.giveItem(username, fillItem);

        connector.server.giveItem(username, placeItem); // give the item to place

        client.setBlock(placeItem, placeAt);

        Thread.sleep(5_000); // TODO sync issues?

        assertEquals(placeMaterial, connector.server.getBlock(placeAt));
    }
}
