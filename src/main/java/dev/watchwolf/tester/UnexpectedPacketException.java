package dev.watchwolf.tester;

import java.io.IOException;

/**
 * A packet arrived with a header this connector cannot parse.
 *
 * This is fatal for the connection it arrived on: every packet is self-describing only through its
 * header, so a header we don't recognise means we also don't know how many argument bytes follow.
 * Returning without draining them leaves the stream offset by an unknown amount and every
 * subsequent read misparses -- which is how a single stray packet used to turn into intermittent
 * `EOFException`s much later on.
 */
public class UnexpectedPacketException extends IOException {
    private final int header;

    public UnexpectedPacketException(int header) {
        super("unknown packet header " + describe(header)
                + "; the arguments that follow it cannot be drained, so the stream is out of sync");
        this.header = header;
    }

    private static String describe(int header) {
        String binary = Integer.toBinaryString(header & 0xFFFF);
        while (binary.length() < 16) binary = "0" + binary;
        return String.format("0x%04X (0b%s)", header, binary);
    }

    public int getHeader() {
        return this.header;
    }
}
