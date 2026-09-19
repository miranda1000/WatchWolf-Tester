package dev.watchwolf.tester;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Reads the two-byte header of an asynchronous packet without ever consuming half of one.
 *
 * The async poll loop cannot block forever on a socket that may simply have nothing to say, so it
 * used to set a one second timeout around a read of *both* header bytes. When the timeout landed
 * between them the first byte was already gone, the exception was swallowed, and the stream stayed
 * one byte out of phase for the rest of the run.
 *
 * The short timeout therefore only covers the wait for the *first* byte -- at that point nothing
 * has been consumed and giving up is free. Once a header has started it is read to completion under
 * a longer timeout; a peer that sends one byte and then stalls is broken, and says so.
 */
public class PacketHeaderReader {
    /**
     * Returned by {@link #read} when the poll window expired before any byte arrived.
     */
    public static final int NO_PACKET = -1;

    public static final int DEFAULT_POLL_TIMEOUT = 1000;
    public static final int DEFAULT_HEADER_TIMEOUT = 10000;

    /**
     * How the reader changes the read deadline of the underlying stream (`Socket::setSoTimeout`).
     */
    public interface TimeoutSetter {
        void setTimeout(int millis) throws IOException;
    }

    private final int pollTimeout;
    private final int headerTimeout;

    public PacketHeaderReader() {
        this(DEFAULT_POLL_TIMEOUT, DEFAULT_HEADER_TIMEOUT);
    }

    public PacketHeaderReader(int pollTimeout, int headerTimeout) {
        this.pollTimeout = pollTimeout;
        this.headerTimeout = headerTimeout;
    }

    /**
     * @param dis Stream to read the header from
     * @param timeouts How to change the read deadline of <dis>
     * @return The header read, or {@link #NO_PACKET} if nothing arrived within the poll window
     * @throws IncompleteHeaderException The header started but never finished
     * @throws IOException The stream failed
     */
    public int read(DataInputStream dis, TimeoutSetter timeouts) throws IOException {
        // nothing consumed yet, so giving up here costs nothing
        timeouts.setTimeout(this.pollTimeout);
        int lsb;
        try {
            lsb = dis.readUnsignedByte();
        } catch (SocketTimeoutException noTrafficYet) {
            return NO_PACKET;
        }

        // a header has started; from here on, stopping half way would desynchronise the stream
        timeouts.setTimeout(this.headerTimeout);
        int msb;
        try {
            msb = dis.readUnsignedByte();
        } catch (SocketTimeoutException ex) {
            throw new IncompleteHeaderException(lsb, this.headerTimeout, ex);
        }

        return (msb << 8) | lsb;
    }

    /**
     * The first byte of a header arrived and the second one never did. The connection is
     * unrecoverable: we have consumed a byte we cannot put back.
     */
    public static class IncompleteHeaderException extends IOException {
        public IncompleteHeaderException(int firstByte, int timeout, Throwable cause) {
            super(String.format("Only got the first byte of a packet header (0x%02X) after waiting %dms for the second one; "
                    + "the connection is out of sync and cannot be recovered.", firstByte, timeout), cause);
        }
    }
}
