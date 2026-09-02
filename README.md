# WatchWolf - Tester [![CodeFactor](https://www.codefactor.io/repository/github/miranda1000/watchwolf-tester/badge/dev)](https://www.codefactor.io/repository/github/miranda1000/watchwolf-tester/overview/dev)

The entry point to [WatchWolf](https://watchwolf.dev/), and the library you depend on to test a
Minecraft plugin. You declare the servers and players you want in a YAML file, write ordinary
JUnit 5 tests, and the Tester orchestrates everything else: it asks the ServersManager for real
Minecraft servers, asks the ClientsManager for real players, runs your tests against them, and
shuts it all down afterwards.

`dev.watchwolf:watchwolf-tester` · **Java 8** · JUnit 5

## Getting started

### 1. Have a WatchWolf environment running

The Tester is a client; it needs a machine running the ServersManager (port 8000) and the
ClientsManager (port 7000). The easiest way to get one is the setup script from the
[WatchWolf](https://github.com/watch-wolf/WatchWolf) repository:

```bash
wget https://raw.githubusercontent.com/watch-wolf/WatchWolf/main/WatchWolfSetup.sh
bash WatchWolfSetup.sh --build
bash WatchWolfSetup.sh --run
```

### 2. Add the dependency

```xml
<dependency>
    <groupId>dev.watchwolf</groupId>
    <artifactId>watchwolf-tester</artifactId>
    <version>0.3.1.1</version>
</dependency>
```

Releases are published to GitHub Packages
(`https://maven.pkg.github.com/miranda1000/watchwolf-tester`).

### 3. Describe the servers you want

```yaml
# src/test/resources/watchwolf.yaml
provider: "127.0.0.1"       # the machine running the ServersManager/ClientsManager

server-type:
  - Spigot:
      - "1.8.8"
      - "1.19"
  - Paper:
      - "1.14"

users:
  - "MinecraftGamer_Z"      # a bot that will join every server

plugin: "MyPlugin"          # the plugin under test
```

### 4. Write a test

```java
@ExtendWith(WorldInteractionShould.class)
public class WorldInteractionShould extends AbstractTest {
    @Override
    public String getConfigFile() {
        return "src/test/resources/watchwolf.yaml";
    }

    @ParameterizedTest
    @ArgumentsSource(WorldInteractionShould.class)
    public void breakBlock(TesterConnector connector) throws Exception {
        String username = connector.getClients()[0];
        ExtendedClientPetition client = connector.getClientPetition(username);

        Position target = client.getPosition().add(0, -1, 0);
        client.breakBlock(target);

        assertEquals(Blocks.AIR, connector.server.getBlock(target),
                     "The block was not broken on " + connector.getServerType());
    }
}
```

The class extends `AbstractTest` and is registered **twice** — as `@ExtendWith` (so it can start
and stop the servers) and as `@ArgumentsSource` (so it can feed one `TesterConnector` per server
type × version). That pair is required, not boilerplate you can drop: the test above runs three
times, once on Spigot 1.8.8, once on Spigot 1.19 and once on Paper 1.14.

`TesterConnector` is the single façade:

| Call | Does |
| --- | --- |
| `connector.server.*` | Server petitions: `setBlock`, `getBlock`, `tp`, `giveItem`, `getInventory`, `runCommand`, `spawnEntity`, `setDifficulty`, … |
| `connector.getClients()` | The bot usernames on this server |
| `connector.getClientPetition(name)` | Drive one bot: `moveTo`, `breakBlock`, `setBlock`, `hit`, `use`, `attack`, `equipItemInHand`, `sendMessage`, `lookAt`, `getPosition`, `getInventory`, … |
| `connector.getServerType()` / `getServerVersion()` | Which server this run is against |

## Configuration reference

| Key | Meaning |
| --- | --- |
| `provider` | Host running the ServersManager and ClientsManager |
| `server-type` | List of `Type: [versions…]`. Any folder present in the ServersManager's `server-types/` works, including custom builds |
| `users` | Bot usernames to spawn, connect and whitelist |
| `plugin` | The plugin under test: a *usual plugin* name, a local path, or a URL |
| `extra-plugins` | Additional plugins, same three forms |
| `maps` | `"<world>": "<zip>"` — expanded into the server's world folder |
| `config-files` | A zip expanded into `plugins/`, or `"<dir>": "<file>"` for a single file |
| `world-type` | `NORMAL` or `FLAT` |
| `seed` | World seed; omit or leave empty for a random one |
| `difficulty` | Initial difficulty |
| `invincible` | Cancel all player damage |
| `timings-directory` | Save a timings report per server here |
| `recordings-directory` | Save each client's video here |

A worked example using every key lives in
[`src/test/java/config/resources/complex.yaml`](src/test/java/config/resources/complex.yaml).

## Compile

Use **Java 8**.

```bash
mvn clean package -Dmaven.test.skip=true
```

### Dependencies

- Maven's `org.junit.jupiter:junit-jupiter-engine:5.8.1`
- Maven's `org.junit.jupiter:junit-jupiter-params:5.8.1`
- Maven's `org.yaml:snakeyaml:1.21`

## Running this repository's own tests

Most of `src/test/java` is an **integration** suite: it starts real servers and real bots, so it
needs a live WatchWolf environment and each suite's `resources/config.yaml` must point `provider`
at it. `config/ConfigLoaderShould` and `versions/CompatibilityCheckerShould` are the two that run
standalone.

The classes are named `*Should` / `*Tester`, which Maven Surefire does not pick up by default —
run them from your IDE, or name one explicitly:

```bash
mvn test -Dtest=ConfigLoaderShould
```

## Note on the shared entities

Besides the Tester itself, this repository still ships the previous generation of the shared
WatchWolf classes under `dev.watchwolf.entities.*`. That is what
[WatchWolf-Server](https://github.com/miranda1000/WatchWolf-Server) links against. Their
replacement is [WatchWolf-Core](https://github.com/watch-wolf/WatchWolf-Core)
(`dev.watchwolf.core.*`), which the ServersManager already uses; the two trees are near-duplicates,
so an entity fix often has to be applied in both.

## Related

- [WatchWolf](https://github.com/watch-wolf/WatchWolf) — the protocol specification and setup script
- [WatchWolf-ServersManager](https://github.com/miranda1000/WatchWolf-ServersManager) — provides the servers
- [WatchWolf-Client](https://github.com/miranda1000/WatchWolf-Client) — provides the players
- [WatchWolf-Server](https://github.com/miranda1000/WatchWolf-Server) — the in-game plugin
