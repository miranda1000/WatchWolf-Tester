package dev.watchwolf.tester;

import dev.watchwolf.core.entities.Container;
import dev.watchwolf.core.entities.Position;
import dev.watchwolf.core.protocol.SocketHelper;
import dev.watchwolf.core.entities.items.Item;
import dev.watchwolf.core.entities.items.ItemType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ClientSocketShould {
    private static class ScriptedSocket extends Socket {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        ScriptedSocket(byte []responses) {
            this.input = new ByteArrayInputStream(responses);
        }

        @Override
        public InputStream getInputStream() {
            return this.input;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.output;
        }

        byte []getRequests() {
            return this.output.toByteArray();
        }
    }

    @Test
    public void readPlayerStateFromTheClientSocket() throws Exception {
        Position expectedPosition = new Position("world", 1.25, 64, -8.5);
        Item []expectedInventory = new Item[]{new Item(ItemType.STONE, (byte) 3), new Item(ItemType.DIAMOND)};
        ArrayList<Byte> responses = new ArrayList<>();

        SocketHelper.addShort(responses, 0b000000010001_1_011);
        SocketHelper.addObject(responses, expectedPosition);
        SocketHelper.addShort(responses, 0b000000010010_1_011);
        SocketHelper.addFloat(responses, -12.5f);
        SocketHelper.addShort(responses, 0b000000010011_1_011);
        SocketHelper.addFloat(responses, 92.25f);
        SocketHelper.addShort(responses, 0b000000010100_1_011);
        SocketHelper.addObject(responses, new Container(expectedInventory));

        ScriptedSocket socket = new ScriptedSocket(SocketHelper.toByteArray(responses));
        ExtendedClientPetition client = new ExtendedClientSocket("Steve", socket,
                (header, input) -> fail("Unexpected async response: " + header),
                petition -> {});

        assertEquals(expectedPosition, client.getPosition());
        assertEquals(-12.5f, client.getPitch());
        assertEquals(92.25f, client.getYaw());
        assertArrayEquals(expectedInventory, client.getInventory().getItems());

        DataInputStream requests = new DataInputStream(new ByteArrayInputStream(socket.getRequests()));
        assertEquals(0b000000010001_0_011, SocketHelper.readShort(requests));
        assertEquals(0b000000010010_0_011, SocketHelper.readShort(requests));
        assertEquals(0b000000010011_0_011, SocketHelper.readShort(requests));
        assertEquals(0b000000010100_0_011, SocketHelper.readShort(requests));
        assertEquals(0, requests.available());
    }
}
