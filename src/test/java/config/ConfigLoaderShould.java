package config;

import dev.watchwolf.core.entities.files.plugins.Plugin;
import dev.watchwolf.core.entities.files.plugins.PluginFactory;
import dev.watchwolf.core.entities.ServerType;
import dev.watchwolf.core.entities.files.plugins.UsualPlugin;
import dev.watchwolf.tester.ConfigFileException;
import dev.watchwolf.tester.TestConfigFileLoader;
import dev.watchwolf.core.protocol.SocketHelper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigLoaderShould {
    private static final String PREFIX = "src/test/java/config/resources";

    @Test
    public void loadSimpleFile() throws IOException {
        TestConfigFileLoader loader = new TestConfigFileLoader(ConfigLoaderShould.PREFIX + "/simple.yaml");

        // server types are free-form strings since custom server softwares became supported
        HashSet<String> expectedServerTypes = new HashSet<>();
        expectedServerTypes.add(ServerType.Spigot.name());

        HashSet<String> expectedServerVersions = new HashSet<>();
        expectedServerVersions.add("1.14");
        expectedServerVersions.add("1.18.1");

        ArrayList<String> expectedUsers = new ArrayList<>();
        expectedUsers.add("MinecraftGamer_Z");

        assertEquals(expectedServerTypes, loader.getServerTypes());
        assertEquals(expectedServerVersions, loader.getServerVersions(ServerType.Spigot));
        assertEquals(expectedUsers, Arrays.asList(loader.getUsers()));
        assertEquals(new UsualPlugin("Residence").toString(), loader.getPlugin().toString());
    }

    @Test
    public void boundTheStartupWaitEvenWhenTheFileSaysNothing() throws IOException {
        TestConfigFileLoader loader = new TestConfigFileLoader(ConfigLoaderShould.PREFIX + "/simple.yaml");

        // an unbounded wait turns "the server never came up" into a run that hangs until CI kills it
        assertEquals(300, loader.getStartupTimeout());
    }

    @Test
    public void readTheStartupTimeoutFromTheFile() throws IOException {
        TestConfigFileLoader loader = new TestConfigFileLoader(ConfigLoaderShould.PREFIX + "/timeout.yaml");

        assertEquals(45, loader.getStartupTimeout());
    }

    @Test
    public void rejectAStartupTimeoutThatCannotElapse() throws IOException {
        TestConfigFileLoader loader = new TestConfigFileLoader(ConfigLoaderShould.PREFIX + "/bad-timeout.yaml");

        assertThrowsExactly(IllegalArgumentException.class, () -> loader.getStartupTimeout());
    }

    @Test
    public void loadErrorFile() throws IOException {
        TestConfigFileLoader loader = new TestConfigFileLoader(ConfigLoaderShould.PREFIX + "/error.yaml");

        assertThrowsExactly(ConfigFileException.class, () -> loader.getPlugin()); // a file must contain (at least) the plugin to test
    }

    @Test
    public void loadComplexFile() throws IOException {
        TestConfigFileLoader loader = new TestConfigFileLoader(ConfigLoaderShould.PREFIX + "/complex.yaml");

        String expectedProvider = "192.168.1.80";

        // server types are free-form strings since custom server softwares became supported
        HashSet<String> expectedServerTypes = new HashSet<>();
        expectedServerTypes.add(ServerType.Spigot.name());
        expectedServerTypes.add(ServerType.Paper.name());

        HashSet<String> expectedSpigotServerVersions = new HashSet<>();
        expectedSpigotServerVersions.add("1.14");
        expectedSpigotServerVersions.add("1.18.1");

        HashSet<String> expectedPaperServerVersions = new HashSet<>();
        expectedPaperServerVersions.add("1.19");

        ArrayList<String> expectedUsers = new ArrayList<>();
        expectedUsers.add("rogermiranda1000");
        expectedUsers.add("MinecraftGamer_Z");

        ArrayList<Plugin> expectedExtraPlugins = new ArrayList<>();
        expectedExtraPlugins.add(PluginFactory.build(ConfigLoaderShould.PREFIX + "/Empty.jar"));
        expectedExtraPlugins.add(PluginFactory.build("https://watchwolf.dev/versions/WatchWolf-0.1-1.8-1.19.jar"));

        String expectedWorld = "world";

        // remember that config-files put the files inside 'plugins/': ServersManager resolves every
        // offset against <server>/plugins, so a correct offset does NOT repeat "plugins/".
        // The map form ("Test2": file) was fixed in 0a16f0e and is correct.
        // FIXME the zip form still offsets by "plugins/", so it lands in <server>/plugins/plugins/.
        //       The expectation below characterises today's behaviour, not the desired one.
        ArrayList<String> expectedConfigFiles = new ArrayList<>();
        expectedConfigFiles.add("plugins/Config.zip");
        expectedConfigFiles.add("Test2/Empty.jar");

        assertEquals(expectedProvider, loader.getProvider());
        assertEquals(expectedServerTypes, loader.getServerTypes());
        assertEquals(expectedSpigotServerVersions, loader.getServerVersions(ServerType.Spigot));
        assertEquals(expectedPaperServerVersions, loader.getServerVersions(ServerType.Paper));
        assertEquals(expectedUsers, Arrays.asList(loader.getUsers()));
        assertEquals(new UsualPlugin("Residence").toString(), loader.getPlugin().toString());
        // the loader keeps the extra plugins in a Set, so their order is not part of the contract.
        // Compare the wire representation because plugin value equality is not part of Core's API.
        Set<List<Byte>> expectedPluginData = expectedExtraPlugins.stream().map(ConfigLoaderShould::serialize).collect(Collectors.toSet());
        Set<List<Byte>> actualPluginData = Arrays.stream(loader.getExtraPlugins()).map(ConfigLoaderShould::serialize).collect(Collectors.toSet());
        assertEquals(expectedPluginData, actualPluginData);
        assertEquals(1, loader.getMaps().length); assertEquals(expectedWorld, loader.getMaps()[0].getWorldName());
        // config files also come out of a Set, so compare without relying on order
        List<String> actualConfigFiles = Arrays.stream(loader.getConfigFiles())
                .map(file -> file.getOffsetPath() + file.getName() + "." + file.getExtension())
                .collect(Collectors.toList());
        assertEquals(expectedConfigFiles.size(), actualConfigFiles.size());
        assertTrue(actualConfigFiles.containsAll(expectedConfigFiles),
                "expected " + expectedConfigFiles + " in any order, got " + actualConfigFiles);
    }

    private static List<Byte> serialize(Plugin plugin) {
        ArrayList<Byte> data = new ArrayList<>();
        SocketHelper.addObject(data, plugin);
        return data;
    }
}
