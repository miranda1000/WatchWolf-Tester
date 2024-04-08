package dev.watchwolf.tester;

import dev.watchwolf.entities.Difficulty;
import dev.watchwolf.entities.PluginBuilder;
import dev.watchwolf.entities.ServerType;
import dev.watchwolf.entities.WorldType;
import dev.watchwolf.entities.files.ConfigFile;
import dev.watchwolf.entities.files.Plugin;
import dev.watchwolf.entities.files.WorldFile;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * All AbstractTest's instances will have one of those objects to get the necessary data in order to run the tests.
 * The problem is that only `beforeAll` needs the information, and JUnit creates multiple instances to fulfill the tests,
 * that's why we won't load any part of the file until it is explicitly requested.
 */
public class TestConfigFileLoader {
    private final String file;
    private HashMap<String,Object> loadedYaml;

    /**
     * Machine with the servers and clients
     */
    private String provider;

    /**
     * Server type, with all its versions
     */
    private HashMap<String,Set<String>> serverType;
    private Boolean overrideSync;
    private Plugin plugin;
    private Set<Plugin> extraPlugins;
    private WorldType worldType;
    private String seed;
    private Set<WorldFile> maps;
    private Set<ConfigFile> configFiles;
    private Set<String> users;

    private Supplier<File> timingsDirectory;
    private Supplier<File> recordingsDirectory;

    private Difficulty difficulty;
    private Boolean invincibleModeEnabled;

    public TestConfigFileLoader(String file) throws IOException {
        this.file = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
    }

    private <T> T getEntry(Function<HashMap<String,Object>,T> getter) {
        Yaml yaml = new Yaml();
        if (this.loadedYaml == null) this.loadedYaml = yaml.load(this.file);
        if (this.loadedYaml == null) return null; // empty file
        try {
            return getter.apply(this.loadedYaml);
        } catch (NullPointerException ignore) {
            return null;
        }
    }

    public boolean reportTimings() {
        return this.getTimingsDirectory() != null;
    }

    public File getTimingsDirectory() throws IllegalArgumentException {
        if (this.timingsDirectory == null) {
            String dir = this.getEntry(it -> (String) it.get("timings-directory"));
            File directory = null;
            if (dir != null) {
                directory = new File(dir);
                if (!directory.isDirectory()) throw new IllegalArgumentException("The timings directory must be a directory");
            }

            final File dirCpy = directory;
            this.timingsDirectory = ()->dirCpy;
        }
        return this.timingsDirectory.get();
    }

    public File getRecordingsDirectory() throws IllegalArgumentException {
        if (this.recordingsDirectory == null) {
            String dir = this.getEntry(it -> (String) it.get("recordings-directory"));
            File directory = null;
            if (dir != null) {
                directory = new File(dir);
                if (!directory.isDirectory()) throw new IllegalArgumentException("The recordings directory must be a directory");
            }

            final File dirCpy = directory;
            this.recordingsDirectory = ()->dirCpy;
        }
        return this.recordingsDirectory.get();
    }

    public WorldType getWorldType() throws IllegalArgumentException {
        if (this.worldType == null) {
            String type = this.getEntry(it -> (String) it.get("world-type"));
            if (type != null) this.worldType = WorldType.valueOf(type.toUpperCase());
            else this.worldType = WorldType.FLAT; // default value
        }
        return this.worldType;
    }

    public String getSeed() throws IllegalArgumentException {
        if (this.seed == null) {
            String seed = this.getEntry(it -> (String) it.get("seed"));
            this.seed = (seed != null) ? seed : "1"; // by default, seed="1"
        }
        return this.seed;
    }


    public Difficulty getDifficulty() throws IllegalArgumentException {
        if (this.difficulty == null) {
            String type = this.getEntry(it -> (String) it.get("difficulty"));
            if (type != null) this.difficulty = Difficulty.valueOf(type.toUpperCase());
            else this.difficulty = Difficulty.PEACEFUL; // default value
        }
        return this.difficulty;
    }

    public boolean getInvincibleModeEnabled() throws IllegalArgumentException {
        if (this.invincibleModeEnabled == null) {
            Boolean type = this.getEntry(it -> (boolean) it.get("invincible"));
            if (type != null) this.invincibleModeEnabled = type;
            else this.invincibleModeEnabled = true; // default value
        }
        return this.invincibleModeEnabled;
    }

    public String getProvider() {
        if (this.provider == null) {
            this.provider = this.getEntry(it -> (String) it.get("provider"));
            if (this.provider == null) this.provider = "127.0.0.1";
        }

        return this.provider;
    }

    public Set<String> getServerTypes() throws IllegalArgumentException {
        if (this.serverType == null) {
            this.serverType = new HashMap<>();

            Set<String> r = this.getEntry(it -> {
                ArrayList<LinkedHashMap<String,Object>> types = (ArrayList<LinkedHashMap<String,Object>>) it.get("server-type");
                final Set<String> re = new HashSet<>();
                for (LinkedHashMap<String,Object> e : types) {
                    re.addAll(e.keySet());
                }
                return re;
            });
            if (r == null) return null; // not found
            for (String type : r) this.serverType.put(type, null);
        }

        return this.serverType.keySet();
    }

    /**
     * Get the versions that will be launched for that server type.
     * The first time it will get loaded.
     * @param serverType Type of server to get the versions
     * @return The versions of that server type; null if no server versions for that type.
     */
    public Set<String> getServerVersions(final String serverType) throws ConfigFileException {
        if (this.serverType == null) this.getServerTypes(); // first load hashmap
        Set<String> versions = this.serverType.get(serverType);
        if (versions == null) {
            Set<String> r = this.getEntry(it -> {
                ArrayList<LinkedHashMap<String,ArrayList<String>>> types = (ArrayList<LinkedHashMap<String,ArrayList<String>>>) it.get("server-type");
                final Set<String> re = new HashSet<>();
                for (LinkedHashMap<String,ArrayList<String>> e : types) {
                    ArrayList<String> wantedElements = e.get(serverType);
                    if (wantedElements != null) re.addAll(wantedElements);
                }
                return re;
            });

            if (r == null || r.size() < 1) return null; // not found / not enough versions for the specified type
            versions = r;
            this.serverType.put(serverType, versions);
        }

        return new HashSet<>(versions);
    }

    public Set<String> getServerVersions(final ServerType serverType) throws ConfigFileException {
        return this.getServerVersions(serverType.name());
    }

    public boolean getOverrideSync() {
        if (this.overrideSync == null) {
            this.overrideSync = false; // TODO
        }

        return this.overrideSync;
    }

    public Plugin getPlugin() throws ConfigFileException {
        if (this.plugin == null) {
            String r = this.getEntry(it -> (String) it.get("plugin"));
            if (r == null) throw new ConfigFileException("The config file must contain a plugin to test.");
            try {
                this.plugin = PluginBuilder.build(r);
            } catch (IOException ex) { throw new ConfigFileException(ex); }
        }

        return this.plugin;
    }

    public Plugin []getExtraPlugins() throws ConfigFileException {
        if (this.extraPlugins == null) {
            this.extraPlugins = new HashSet<>();
            ArrayList<String> r = this.getEntry(it -> (ArrayList<String>) it.get("extra-plugins"));
            if (r != null) { // maybe any extra plugin?
                for (String path : r) {
                    try {
                        this.extraPlugins.add(PluginBuilder.build(path));
                    } catch (IOException ex) { throw new ConfigFileException("Couldn't load " + path, ex); }
                }
            }
        }

        return this.extraPlugins.toArray(new Plugin[0]);
    }

    public String []getUsers() {
        if (this.users == null) {
            this.users = new HashSet<>();
            ArrayList<String> r = this.getEntry(it -> (ArrayList<String>) it.get("users"));
            if (r != null) { // maybe any user?
                this.users.addAll(r);
            }
        }

        return this.users.toArray(new String[0]);
    }

    public WorldFile []getMaps() throws ConfigFileException {
        if (this.maps == null) {
            this.maps = new HashSet<>();
            final AtomicReference<String> crash = new AtomicReference<>("");
            ArrayList<WorldFile> r = this.getEntry(it -> {
                ArrayList<WorldFile> worlds = new ArrayList<>();
                for (LinkedHashMap<String,String> w : (ArrayList<LinkedHashMap<String,String>>)it.get("maps")) {
                    for (Map.Entry<String,String> map : w.entrySet()) {
                        try {
                            worlds.add(new WorldFile(map.getKey(), map.getValue()));
                        } catch (IOException ex) {
                            crash.set(map.getKey() + " - " + map.getValue());
                            return worlds;
                        }
                    }
                }
                return worlds;
            });

            if (crash.get().length() > 0) throw new ConfigFileException("Error while loading world file '" + crash.get() + "'");
            if (r != null) this.maps.addAll(r);
        }

        return this.maps.toArray(new WorldFile[0]);
    }

    public ConfigFile []getConfigFiles() throws ConfigFileException {
        if (this.configFiles == null) {
            this.configFiles = new HashSet<>();
            final AtomicReference<String> crash = new AtomicReference<>("");
            ArrayList<ConfigFile> r = this.getEntry(it -> {
                ArrayList<ConfigFile> files = new ArrayList<>();
                for (Object f : (ArrayList<Object>)it.get("config-files")) {
                    if (f instanceof String) {
                        try {
                            files.add(new ConfigFile((String) f, "plugins/"));
                        } catch (IOException ex) {
                            crash.set((String) f);
                            return files;
                        }
                    }
                    else if (f instanceof LinkedHashMap) {
                        for (Map.Entry<String,String> file : ((LinkedHashMap<String,String>)f).entrySet()) {
                            try {
                                files.add(new ConfigFile(file.getValue(), file.getKey()));
                            } catch (IOException ex) {
                                crash.set(file.getValue());
                                return files;
                            }
                        }
                    }
                    else {
                        crash.set("typeof " + f.getClass().toString());
                        return files;
                    }
                }
                return files;
            });

            if (crash.get().length() > 0) throw new ConfigFileException("Error while loading config file '" + crash.get() + "'");
            if (r != null) this.configFiles.addAll(r);
        }

        return this.configFiles.toArray(new ConfigFile[0]);
    }
}
