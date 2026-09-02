package generic;

import dev.watchwolf.entities.items.Item;
import dev.watchwolf.entities.items.ItemType;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.ExtendedClientPetition;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ITItemsTester.class)
public class ITItemsTester extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    /**
     * This test doesn't touch the connector, but validates the enum.
     * @param connector The object to access the server/clients
     */
    @ParameterizedTest
    @ArgumentsSource(ITItemsTester.class)
    public void checkEnum(TesterConnector connector) {
        assertEquals(ItemType.values()[0], ItemType.AIR); // (by definition) AIR must be the first one
    }

    @ParameterizedTest
    @ArgumentsSource(ITItemsTester.class)
    public void checkItems(TesterConnector connector) throws Exception {
        String user = connector.getClients()[0];
        ExtendedClientPetition petition = connector.getClientPetition(user);

        int numOK = 0;
        for (ItemType type : ItemType.values()) {
            System.out.println("Validating item " + type.name() + "...");
            Item give = new Item(type);
            connector.server.giveItem(user, give);

            ArrayList<Item> expected = new ArrayList<>();
            expected.add(give);
            if (expected.equals(Arrays.asList(petition.getInventory().getItems()))) numOK++;
            else System.out.println("[e] Failed validation on " + type.name());

            connector.server.runCommand("clear " + user); // clear inventory
        }

        assertEquals(ItemType.values().length, numOK);
    }
}
