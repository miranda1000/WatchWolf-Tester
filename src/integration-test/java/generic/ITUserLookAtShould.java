package generic;

import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.blocks.Blocks;
import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.ExtendedClientPetition;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ITUserLookAtShould.class) // run the tests with the AbstractTest overridden methods
public class ITUserLookAtShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/integration-test/java/generic/resources/config.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(ITTesterTester.class)
    public void lookAtTheExactSameSpot(TesterConnector connector) throws Exception {
        ExtendedClientPetition client = connector.getClientPetition(0);

        // all the tests, with [pitch,yaw]
        float [][]tests = new float[][] {
                new float[]{-89, 0}, // top, south
                new float[]{-90, 0}, // top, south
                new float[]{0, 179}, // horizontal, north
                new float[]{0, -179}, // horizontal, north
                new float[]{89,-90}, // bottom, east
                new float[]{20,-70} // a bit down, south-east
        };

        int tries = 5;
        for (float []test : tests) {
            float pitch = test[0],
                    yaw = test[1];

            boolean succeed;
            do {
                client.lookAt(pitch, yaw); // try some times; sometimes the chunks aren't loaded, so it fails

                succeed = checkCamera(client.getYaw(), client.getPitch(), yaw, pitch, tries > 0);
                if (!succeed) {
                    tries--;
                    Thread.sleep(5000); // try again later
                }
            } while (!succeed);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(ITTesterTester.class)
    public void moveTheCameraToTheBlock(TesterConnector connector) throws Exception {
        ExtendedClientPetition client = connector.getClientPetition(0);

        Position from = new Position("world", -229.5, 4, -197.5);
        // as we're looking at blocks we need to look at its center
        Position []examples = new Position[] {
                new Position("world", -227.5, 4.5, -194.5),
                new Position("world", -229.5, 4.5, -194.5),
                new Position("world", -229.5, 5.5, -194.5),
                new Position("world", -231.5, 4.5, -194.5),
                new Position("world", -229.5, 4.5, -198.5),
                new Position("world", -229.5, 3.5, -197.5),
                new Position("world", -229.5, 6.5, -197.5),
                new Position("world", -227.5, 5.5, -194.5),
                new Position("world", -230.5, 4.5, -197.5),
                new Position("world", -227.5, 5.5, -195.5),
                new Position("world", -230.5, 5.5, -196.5),
                new Position("world", -228.5, 5.5, -195.5)
        };
        // all the results, with [yaw,pitch]
        Float [][]expected = new Float[][] {
                new Float[]{-33f, 20f},
                new Float[]{0f, 20f},
                new Float[]{0f, 2f},
                new Float[]{36f, 20f},
                new Float[]{180f, 50f},
                new Float[]{null, 90f},
                new Float[]{null, -90f},
                new Float[]{-33f, 3f},
                new Float[]{90f, 50f},
                new Float[]{-40f, 3f},
                new Float[]{40f, 6f},
                new Float[]{-30f, 3f}
        };

        int tries = 5;
        for (int i = 0; i < examples.length; i++) {
            Position pos = examples[i].add(-from.getX(), -from.getY(), -from.getZ()).add(client.getPosition()); // offset to avoid teleporing the player

            boolean succeed;
            do {
                client.lookAt(pos); // try some times; sometimes the chunks aren't loaded, so it fails

                succeed = checkCamera(client.getYaw(), client.getPitch(), expected[i][0], expected[i][1], tries > 0);
                if (!succeed) {
                    tries--;
                    Thread.sleep(5000); // try again later
                }
            } while (!succeed);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(ITTesterTester.class)
    public void moveTheCameraWhileLoadingChunks(TesterConnector connector) throws Exception {
        String clientUsername = connector.getClients()[0];
        ExtendedClientPetition client = connector.getClientPetition(0);

        Position target = new Position("world", 10000, 100, 10000);
        float pitch = 20f,
                yaw = 20f;

        // prepare the zone
        connector.server.setBlock(target, Blocks.AIR);
        connector.server.setBlock(target.add(0,1,0), Blocks.AIR);
        connector.server.setBlock(target.add(0,-1,0), Blocks.STONE);

        // run simultaneously
        connector.server.tp(clientUsername, target);
        client.lookAt(pitch, yaw);

        // ok?
        checkCamera(client.getYaw(), client.getPitch(), yaw, pitch, false);
    }

    private boolean checkCamera(float yaw, float pitch, Float expectedYaw, Float expectedPitch, boolean permissive) throws Exception {
        float ERROR_MARGIN = 5f;

        if (expectedYaw != null && Math.abs(Math.abs(expectedYaw)-180f) <= ERROR_MARGIN) {
            // we're on the border of the yaw; it may be possible that it is -180 instead of +180
            if (Math.abs(Math.abs(yaw)-180f) <= ERROR_MARGIN) yaw = expectedYaw; // succeeded; force it so the comparison it's ok
        }

        System.out.println(yaw + "/" + pitch);
        //System.out.println("expected " + (expectedYaw == null ? yaw : expectedYaw) + "/" + (expectedPitch == null ? pitch : expectedPitch));

        boolean succeed = Math.abs(yaw - (expectedYaw == null ? yaw : expectedYaw)) <= ERROR_MARGIN
                && Math.abs(pitch - (expectedPitch == null ? pitch : expectedPitch)) <= ERROR_MARGIN;
        if (!permissive) assertTrue(succeed, yaw + "/" + pitch + " (expected " + (expectedYaw == null ? yaw : expectedYaw) + "/" + (expectedPitch == null ? pitch : expectedPitch) + ")");
        else if (!succeed) System.out.println("[failed]");
        return succeed;
    }
}
