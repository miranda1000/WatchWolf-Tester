package dev.watchwolf.tester;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The async poll loop used to read both header bytes under the same one second timeout and swallow
 * the `SocketTimeoutException`. A timeout landing between the two bytes therefore ate the first one
 * and left the stream one byte out of phase for the rest of the run -- the "works sometimes" in the
 * bug report.
 */
public class PacketHeaderReaderShould {
    /**
     * A stream that times out at a chosen offset, the way a socket with a `soTimeout` does.
     */
    private static class StallingInputStream extends InputStream {
        private final byte []data;
        private final int stallAt;
        private int read = 0;

        StallingInputStream(int stallAt, int... data) {
            this.stallAt = stallAt;
            this.data = new byte[data.length];
            for (int n = 0; n < data.length; n++) this.data[n] = (byte) data[n];
        }

        @Override
        public int read() throws IOException {
            if (this.read == this.stallAt) throw new SocketTimeoutException("Read timed out");
            return this.data[this.read++] & 0xFF;
        }
    }

    private static DataInputStream streamOf(int... bytes) {
        byte []data = new byte[bytes.length];
        for (int n = 0; n < bytes.length; n++) data[n] = (byte) bytes[n];
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    private static final PacketHeaderReader.TimeoutSetter IGNORE_TIMEOUTS = (millis) -> {};

    @Test
    public void readHeadersLeastSignificantByteFirst() throws IOException {
        assertEquals(0x0102, new PacketHeaderReader().read(streamOf(0x02, 0x01), IGNORE_TIMEOUTS));
    }

    @Test
    public void reportNoPacketWhenNothingArrives() throws IOException {
        // stalls before the first byte: nothing has been consumed, so giving up costs nothing
        DataInputStream dis = new DataInputStream(new StallingInputStream(0, 0x02, 0x01));

        assertEquals(PacketHeaderReader.NO_PACKET, new PacketHeaderReader().read(dis, IGNORE_TIMEOUTS));
    }

    @Test
    public void refuseToSwallowAHalfReadHeader() {
        // stalls between the two header bytes: the first byte is gone and cannot be put back
        DataInputStream dis = new DataInputStream(new StallingInputStream(1, 0x02, 0x01));

        PacketHeaderReader.IncompleteHeaderException ex = assertThrows(PacketHeaderReader.IncompleteHeaderException.class,
                () -> new PacketHeaderReader().read(dis, IGNORE_TIMEOUTS));
        assertTrue(ex.getMessage().contains("0x02"), "the message must name the byte that was consumed: " + ex.getMessage());
    }

    @Test
    public void pollOnlyWhileWaitingForTheFirstByte() throws IOException {
        List<Integer> timeouts = new ArrayList<>();

        new PacketHeaderReader(1000, 10000).read(streamOf(0x02, 0x01), timeouts::add);

        // short window to notice there is nothing to read; long one once a header has started
        assertEquals(java.util.Arrays.asList(1000, 10000), timeouts);
    }

    @Test
    public void notWidenTheTimeoutWhenThereIsNoPacket() throws IOException {
        List<Integer> timeouts = new ArrayList<>();

        new PacketHeaderReader(1000, 10000).read(new DataInputStream(new StallingInputStream(0)), timeouts::add);

        assertEquals(java.util.Collections.singletonList(1000), timeouts);
    }
}
