package generic;

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

@ExtendWith(ITPositionsShould.class)
public class ITPositionsShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    /**
     * Blocks measure 1m, so they have multiple valid world positions.
     * One block found in (x,y,z) should still be the same block if
     * (x+0.5,y+0.5,z+0.5) is provided instead of its real position.
     */
    @ParameterizedTest
    @ArgumentsSource(ITPositionsShould.class)
    public void roundBlockPosition(TesterConnector connector) throws Exception {
        Position blockPosition = new Position("world", 0,0,0),
                sameBlockPosition = blockPosition.add(0.5,0.5,0.5);
        Block initialBlock = Blocks.DIAMOND_BLOCK,
                finalBlock = Blocks.LAPIS_BLOCK;

        connector.server.setBlock(blockPosition, initialBlock);
        connector.server.setBlock(sameBlockPosition, finalBlock);

        assertEquals(connector.server.getBlock(blockPosition), connector.server.getBlock(sameBlockPosition));
        assertEquals(connector.server.getBlock(blockPosition), finalBlock); // the block should be LAPIS_BLOCK
    }

    /**
     * Same as `roundBlockPosition`, but specifying what block the player should break
     */
    @ParameterizedTest
    @ArgumentsSource(ITPositionsShould.class)
    public void roundBlockPositionToBreak(TesterConnector connector) throws Exception {
        ExtendedClientPetition clientPetition = connector.getClientPetition(0);

        Position blockPosition = clientPetition.getPosition().getBlockPosition()
                                        .add(1,0,0), // block adjacent to the player
                sameBlockPosition = blockPosition.add(0.5,0.5,0.5);

        connector.server.setBlock(blockPosition, Blocks.DIRT);

        clientPetition.breakBlock(sameBlockPosition);

        assertEquals(Blocks.AIR, connector.server.getBlock(blockPosition));
    }

    /**
     * Same as `roundBlockPosition`, but specifying where the player should place a block
     */
    @ParameterizedTest
    @ArgumentsSource(ITPositionsShould.class)
    public void roundBlockPositionToPlace(TesterConnector connector) throws Exception {
        String nickname = connector.getClients()[0];
        ExtendedClientPetition clientPetition = connector.getClientPetition(0);

        Position placingAt = clientPetition.getPosition().getBlockPosition()
                                    .add(1,0,0), // block adjacent to the player
                sameBlockPosition = placingAt.add(0.5,0.5,0.5);
        Block placing = Blocks.DIRT;
        Item placingItem = new Item(placing.getItemType());

        connector.server.setBlock(placingAt, Blocks.AIR);
        connector.server.setBlock(placingAt.add(0,-1,0), Blocks.DIRT); // make sure the player can place the block

        connector.server.giveItem(nickname, placingItem); // give the item the player needs to place

        clientPetition.setBlock(placingItem, sameBlockPosition);

        assertEquals(placing, connector.server.getBlock(placingAt));
    }

    /**
     * If the player is requested to break a block from another world, it should fail
     */
    @ParameterizedTest
    @ArgumentsSource(ITPositionsShould.class)
    public void checkTheWorld(TesterConnector connector) throws Exception {
        ExtendedClientPetition clientPetition = connector.getClientPetition(0);

        Position blockPosition = clientPetition.getPosition().getBlockPosition()
                                        .add(1,0,0); // block adjacent to the player
        Position counterWorldBlockPosition = new Position("none", blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
        Block placing = Blocks.DIRT;

        connector.server.setBlock(blockPosition, placing);

        clientPetition.breakBlock(counterWorldBlockPosition);

        // the block needs to be the same (as you can't break a block from another world)
        assertEquals(placing, connector.server.getBlock(blockPosition));
    }
}
