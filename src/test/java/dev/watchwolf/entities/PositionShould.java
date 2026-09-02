package dev.watchwolf.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Skeleton coverage for Position. Deliberately small; see {@link SocketHelperShould}.
 */
public class PositionShould {
    @Test
    public void addOffsets() {
        Position base = new Position("world", 10.0, 64.0, -5.0);

        assertEquals(new Position("world", 10.0, 63.0, -5.0), base.add(0, -1, 0));
    }

    @Test
    public void keepTheWorldWhenAddingOffsets() {
        Position base = new Position("nether", 1.0, 2.0, 3.0);

        assertEquals("nether", base.add(1, 1, 1).getWorld());
    }

    @Test
    public void refuseToAddPositionsOfDifferentWorlds() {
        Position overworld = new Position("world", 0.0, 0.0, 0.0);
        Position nether = new Position("nether", 0.0, 0.0, 0.0);

        assertThrows(IllegalArgumentException.class, () -> overworld.add(nether));
    }

    @Test
    public void truncatePositiveCoordinatesToBlocks() {
        Position position = new Position("world", 3.7, 64.2, 0.5);

        assertEquals(3, position.getBlockX());
        assertEquals(64, position.getBlockY());
        assertEquals(0, position.getBlockZ());
    }

    @Test
    public void truncateNegativeCoordinatesTowardsZero() {
        // FIXME Minecraft block coordinates floor, so x=-0.5 is block -1. getBlock*() casts to int,
        //       which truncates towards zero and yields 0. This characterises today's behaviour,
        //       not the desired one.
        Position position = new Position("world", -0.5, -1.2, -64.9);

        assertEquals(0, position.getBlockX());
        assertEquals(-1, position.getBlockY());
        assertEquals(-64, position.getBlockZ());
    }

    @Test
    public void distinguishPositionsInDifferentWorlds() {
        assertNotEquals(new Position("world", 1.0, 2.0, 3.0),
                        new Position("nether", 1.0, 2.0, 3.0));
    }
}
