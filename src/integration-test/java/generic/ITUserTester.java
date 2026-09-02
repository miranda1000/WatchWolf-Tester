package generic;

import dev.watchwolf.client.ClientPetition;
import dev.watchwolf.client.MessageNotifier;
import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.blocks.Block;
import dev.watchwolf.entities.blocks.Blocks;
import dev.watchwolf.entities.items.Item;
import dev.watchwolf.entities.items.ItemType;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.ExtendedClientPetition;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ITUserTester.class) // run the tests with the AbstractTest overridden methods
public class ITUserTester extends AbstractTest {
    private static final String USER1 = "MinecraftGamer_Z";

    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    /**
     * For the test `getCommandReply` we need USER1 to be OP
     */
    @Override
    public void beforeAll(ExtensionContext extensionContext) throws IOException {
        super.beforeAll(extensionContext); // start the servers & clients

        // move the player to its desired location & give him a tool and the MineIt tool
        super.provideArguments(extensionContext).map(arg -> (TesterConnector)arg.get()[0]).forEach(server -> {
            try {
                server.server.opPlayer(USER1);
            } catch (IOException ignore) {}
        });
    }

    @ParameterizedTest
    @ArgumentsSource(ITUserTester.class)
    public void sendMsg(TesterConnector connector) throws Exception {
        String user = ITUserTester.USER1,
                message = "Hello World!";

        final Object got = new Object();
        MessageNotifier notifier = (author,msg) -> {
            if (author.equals(user) && msg.equals(message)) {
                // notify
                synchronized (got) {
                    got.notify();
                }
            }
        };
        connector.addOnMessage(notifier);
        synchronized (got) {
            connector.getClientPetition(user).sendMessage(message);
            got.wait(4000); // wait 4s
        }
        connector.removeOnMessage(notifier);
    }

    @ParameterizedTest
    @ArgumentsSource(ITUserTester.class)
    public void breakBlock(TesterConnector connector) throws Exception {
        String user = ITUserTester.USER1;
        ClientPetition userPetition = connector.getClientPetition(user);

        Position pos = connector.server.getPlayerPosition(user).add(0, -1, 0);

        Block target_block = Blocks.DIRT;
        connector.setBlock(pos, target_block);

        userPetition.breakBlock(pos);

        assertEquals(Blocks.AIR, connector.server.getBlock(pos));
    }

    @ParameterizedTest
    @ArgumentsSource(ITUserTester.class)
    public void placeBlock(TesterConnector connector) throws Exception {
        String user = ITUserTester.USER1;
        ExtendedClientPetition userPetition = connector.getClientPetition(user);

        Position pos = connector.server.getPlayerPosition(user).add(1, 0, 0);

        // make sure the player can place this block
        connector.setBlock(pos, Blocks.AIR);
        connector.setBlock(pos.add(0,-1,0), Blocks.STONE);

        Block target_block = Blocks.DIRT;

        connector.server.giveItem(user, new Item(target_block.getItemType(), (byte) 2)); // make sure that it find the item with 2 blocks
        userPetition.setBlock(target_block, pos);

        assertEquals(target_block, connector.server.getBlock(pos));
    }

    /**
     * Break a stone block (test give item and equip item and break block)
     * @param connector The object to access the server/clients
     * @throws Exception IOException or assertion failed
     */
    @ParameterizedTest
    @ArgumentsSource(ITUserTester.class)
    public void equipOnHand(TesterConnector connector) throws Exception {
        String user = ITUserTester.USER1;
        ClientPetition userPetition = connector.getClientPetition(user);

        Position pos = connector.server.getPlayerPosition(user).add(0, -1, 0);

        Block target_block = Blocks.STONE;
        Item pickaxe = new Item(ItemType.DIAMOND_PICKAXE);
        connector.setBlock(pos, target_block);
        connector.giveItem(user, new Item(ItemType.DIAMOND)); // we don't want to add the pickaxe in the first slot
        connector.giveItem(user, pickaxe);

        userPetition.equipItemInHand(pickaxe);
        userPetition.breakBlock(pos);

        assertEquals(Blocks.AIR, connector.server.getBlock(pos));
    }

    @ParameterizedTest
    @ArgumentsSource(ITUserTester.class)
    public void moveAt(TesterConnector connector) throws Exception {
        String user = ITUserTester.USER1;
        ClientPetition userPetition = connector.getClientPetition(user);

        Position pos = connector.server.getPlayerPosition(user).add(1, 0, 0);
        int blocksTraveled = 5;

        // make sure that the player can go to that block
        for (int n = 0; n < blocksTraveled; n++) {
            connector.setBlock(pos, Blocks.AIR);
            connector.setBlock(pos.add(0, 1, 0), Blocks.AIR);
            connector.setBlock(pos.add(0, -1, 0), Blocks.STONE);
            if (n != blocksTraveled-1) pos = pos.add(1, 0, 0);
        }

        userPetition.moveTo(pos);

        Position currentPosition = connector.server.getPlayerPosition(user);
        if (!currentPosition.equals(pos, 0.3f)) assertEquals(pos, currentPosition); // not equal => force error
    }

    @ParameterizedTest
    @ArgumentsSource(ITUserTester.class)
    public void getCommandReply(TesterConnector connector) throws Exception {
        String user = ITUserTester.USER1;
        ExtendedClientPetition userPetition = connector.getClientPetition(user);

        String got = userPetition.runCommand("tps");
        System.out.println("Got as a `tps` reply: " + got);

        assertTrue(got.contains("TPS from last 1m, 5m, 15m:"));
    }
}
