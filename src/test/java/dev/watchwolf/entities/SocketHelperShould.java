package dev.watchwolf.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Skeleton coverage for the wire codec. Deliberately small: it exists so the unit suite has
 * something to protect while the socket framing is reworked, not to be exhaustive.
 */
public class SocketHelperShould {
    private static DataInputStream streamOf(ArrayList<Byte> written) {
        return new DataInputStream(new ByteArrayInputStream(SocketHelper.toByteArray(written)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 255, 256, 4096, 65535})
    public void roundTripShorts(int value) throws IOException {
        ArrayList<Byte> out = new ArrayList<>();
        SocketHelper.addShort(out, value);

        assertEquals(2, out.size(), "a short must occupy exactly 2 bytes");
        assertEquals(value, SocketHelper.readShort(streamOf(out)));
    }

    @Test
    public void writeShortsLeastSignificantByteFirst() {
        ArrayList<Byte> out = new ArrayList<>();
        SocketHelper.addShort(out, 0x0102);

        // the header is read back as LSB-then-MSB, so the order on the wire is part of the contract
        assertEquals((byte) 0x02, (byte) out.get(0));
        assertEquals((byte) 0x01, (byte) out.get(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "Spigot", "1.16.5", "MinecraftGamer_Z"})
    public void roundTripStrings(String value) throws IOException {
        ArrayList<Byte> out = new ArrayList<>();
        SocketHelper.addString(out, value);

        assertEquals(value, SocketHelper.readString(streamOf(out)));
    }

    @Test
    public void prefixStringsWithTheirLength() throws IOException {
        ArrayList<Byte> out = new ArrayList<>();
        SocketHelper.addString(out, "abc");

        DataInputStream dis = streamOf(out);
        assertEquals(3, SocketHelper.readShort(dis), "a string starts with its length");
    }

    @Test
    public void roundTripBooleans() throws IOException {
        for (boolean value : new boolean[]{true, false}) {
            ArrayList<Byte> out = new ArrayList<>();
            SocketHelper.addBool(out, value);

            assertEquals(value, SocketHelper.readBool(streamOf(out)));
        }
    }

    @Test
    public void roundTripDoubles() throws IOException {
        ArrayList<Byte> out = new ArrayList<>();
        SocketHelper.addDouble(out, 12.5d);

        assertEquals(12.5d, SocketHelper.readDouble(streamOf(out)), 0.0d);
    }
}
