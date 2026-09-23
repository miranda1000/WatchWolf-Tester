package generic;

import dev.watchwolf.core.entities.Position;
import dev.watchwolf.core.entities.blocks.Block;
import dev.watchwolf.core.entities.blocks.Blocks;
import dev.watchwolf.core.entities.blocks.Directionable;
import dev.watchwolf.core.entities.blocks.Orientable;
import dev.watchwolf.core.entities.blocks.special.Bell;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import dev.watchwolf.core.protocol.SocketHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ITBlocksTester.class) // run the tests with the AbstractTest overridden methods
public class ITBlocksTester extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(ITBlocksTester.class)
    public void setBlock(TesterConnector connector) throws Exception {
        Position p = new Position("world", 0,0,0);
        connector.server.setBlock(p, Blocks.IRON_BLOCK);
        assertEquals(connector.server.getBlock(p), Blocks.IRON_BLOCK);
    }

    @ParameterizedTest
    @ArgumentsSource(ITBlocksTester.class)
    public void setComplexBlock(TesterConnector connector) throws Exception {
        Position p = new Position("world", 0,0,0);
        connector.server.setBlock(p, Blocks.OAK_SLAB);
        assertEquals(connector.server.getBlock(p), Blocks.OAK_SLAB);
    }

    @ParameterizedTest
    @ArgumentsSource(ITBlocksTester.class)
    public void setChangedOrientableBlock(TesterConnector connector) throws Exception {
        Position p = new Position("world", 0,0,0);
        Block slab = (Block) Blocks.ACACIA_SLAB.setOrientation(Orientable.Orientation.U, true);
        connector.server.setBlock(p, slab);
        Block get = connector.server.getBlock(p);

        ArrayList<Byte> originalData = new ArrayList<>(),
                gettedData = new ArrayList<>();
        SocketHelper.addObject(originalData, slab);
        SocketHelper.addObject(gettedData, get);
        assertEquals(originalData, gettedData);
    }

    @ParameterizedTest
    @ArgumentsSource(ITBlocksTester.class)
    public void setChangedOrientableDirectionableBlock(TesterConnector connector) throws Exception {
        Position p = new Position("world", 0,0,0);
        Bell bell = (Bell) Blocks.BELL.setOrientation(Orientable.Orientation.W, true);
        bell = (Bell) bell.setDirection(Directionable.Direction.SINGLE_WALL);
        connector.server.setBlock(p, bell);
        Block get = connector.server.getBlock(p);

        ArrayList<Byte> originalData = new ArrayList<>(),
                gettedData = new ArrayList<>();
        SocketHelper.addObject(originalData, bell);
        SocketHelper.addObject(gettedData, get);
        assertEquals(originalData, gettedData);
    }

    @Test
    public void getRelevantDataFromFunction() throws Exception {
        assertEquals(Blocks.TURTLE_EGG.toString(), "TURTLE_EGG{groupAmount=1}");
    }
}
