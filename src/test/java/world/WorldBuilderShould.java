package world;

import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.Version;
import dev.watchwolf.entities.blocks.Blocks;
import dev.watchwolf.entities.entities.EntityType;
import dev.watchwolf.entities.entities.Zombie;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.ExtendedClientPetition;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(WorldBuilderShould.class)
public class WorldBuilderShould extends AbstractTest {

    @Override
    public String getConfigFile() {
        return "src/test/java/generic/resources/config.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(WorldBuilderShould.class)
    public void generateFlatWorldIfNoOptionProvided(TesterConnector connector) throws Exception {

        Position bedrockPosition = new Position("world", 0,getMinBlockInFlatWorld(connector.getServerVersion()),0);

        // TODO what if a village spawns?
        assertEquals(Blocks.BEDROCK, connector.getBlock(bedrockPosition));
        assertEquals(Blocks.DIRT, connector.getBlock(bedrockPosition.add(0,1,0)));
        assertEquals(Blocks.DIRT, connector.getBlock(bedrockPosition.add(0,2,0)));
        assertEquals(Blocks.GRASS_BLOCK, connector.getBlock(bedrockPosition.add(0,3,0)));
        assertEquals(Blocks.AIR, connector.getBlock(bedrockPosition.add(0,4,0)));
    }

    /**
     * Worlds should be medium by default
     */
    @ParameterizedTest
    @ArgumentsSource(WorldBuilderShould.class)
    public void allowEnemiesIfNoOptionProvided(TesterConnector connector) throws Exception {
        ExtendedClientPetition clientPetition = connector.getClientPetition(0);
        Position spawnAt = clientPetition.getPosition();

        connector.spawnEntity(new Zombie(spawnAt));

        assertTrue(Arrays.asList(connector.getEntities(spawnAt, 10)).stream().anyMatch(e -> e.getType().equals(EntityType.ZOMBIE)),
                "Expected world with mobs, but found no zombies in the region");
    }

    /**
     * Players shouldn't take damage by default
     */
    @ParameterizedTest
    @ArgumentsSource(WorldBuilderShould.class)
    public void preventEnemyDamageIfNoOptionProvided(TesterConnector connector) throws Exception {
        ExtendedClientPetition clientPetition = connector.getClientPetition(0);

        connector.server.setBlock(clientPetition.getPosition(), Blocks.LAVA);

        Thread.sleep(5000); // at this point the user should be dead

        Position testBlock = clientPetition.getPosition().add(0,-1,0);
        connector.server.setBlock(testBlock, Blocks.DIRT);
        clientPetition.breakBlock(testBlock); // TODO REVISIT: the player may be displaced by the lava, and then he will be out of the block range

        assertEquals(Blocks.AIR, connector.server.getBlock(testBlock),
                "The player didn't break the block after swiming in lava (is it dead?)");
    }

    /**
     * Worlds should have the spawn chunk protection disabled
     */
    @ParameterizedTest
    @ArgumentsSource(WorldBuilderShould.class)
    public void allowUserInteractionInSpawnChunks(TesterConnector connector) throws Exception {
        ExtendedClientPetition clientPetition = connector.getClientPetition(0);
        Position breakPos = clientPetition.getPosition().add(0,-1,0); // break one block in the spawn chunks

        connector.server.setBlock(breakPos, Blocks.DIRT);
        clientPetition.breakBlock(breakPos);

        assertEquals(Blocks.AIR, connector.server.getBlock(breakPos));
    }

    public static int getMinBlockInFlatWorld(String mcVersion) {
        Version versionWithNegativeMinBlock = new Version("1.18"),
                currentVersion = new Version(mcVersion);

        if (currentVersion.compareTo(versionWithNegativeMinBlock) >= 0) return -64;
        return 0;
    }
}
