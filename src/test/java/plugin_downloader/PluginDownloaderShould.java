package plugin_downloader;

import dev.watchwolf.tester.AbstractTest;
import dev.watchwolf.tester.TesterConnector;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PluginDownloaderShould.class)
public class PluginDownloaderShould extends AbstractTest {

    @Override
    public String getConfigFile() {
        return "src/test/java/plugin_downloader/resources/config.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(PluginDownloaderShould.class)
    public void downloadRemotePluginAndLoadIt(TesterConnector connector) throws Exception {
        String pluginsCommandReturn = connector.runCommand("plugins");
        System.out.println("[v] Return of `plugins` command: " + pluginsCommandReturn);
        assertTrue(pluginsCommandReturn.contains("PortalGun"), "Expected PortalGun to be available after specifying remote link; got otherwise instead.\nList of plugins: " + pluginsCommandReturn);
    }
}
