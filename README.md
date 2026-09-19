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
    <version>0.3.2</version>
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
| `startup-timeout` | Seconds to wait for a server to become ready before the setup fails. Default `300` |

A worked example using every key lives in
[`src/test/java/config/resources/complex.yaml`](src/test/java/config/resources/complex.yaml).

## Compile

Use **Java 8**. Everything runs inside Docker, so the host needs nothing but Docker itself:

```bash
./ci/build.sh --preclean      # -> target/watchwolf-tester-<version>.jar
```

### Dependencies

- Maven's `org.junit.jupiter:junit-jupiter-engine:5.8.1`
- Maven's `org.junit.jupiter:junit-jupiter-params:5.8.1`
- Maven's `org.yaml:snakeyaml:1.21`

## Running this repository's own tests

There are three suites, kept in separate source roots:

| | Unit | System / integration | Code checks |
| --- | --- | --- | --- |
| Source root | `src/test/java` | `src/integration-test/java` | `src/validation-test/java` |
| Naming | `*Should` | `IT*` | `*Should` |
| Maven profile | `default` | `-P integration-test` | `-P validation-test` |
| Needs a live WatchWolf environment | no | **yes** | no |

```bash
./ci/tests.sh --unit                          # fast, hermetic
./ci/tests.sh --unit --tests 'ConfigLoaderShould'
./ci/tests.sh --integration                   # needs a running environment (checked first)
./ci/validator.sh                             # code checks (naming, system-test timeouts)
```

The system tests start real Minecraft servers and real bots, so they need a ServersManager on port
8000 and a ClientsManager on port 7000, and each suite's `resources/config.yaml` must point
`provider` at that machine. They are excluded from the default build on purpose.

`./ci/tests.sh --integration` checks both ports before doing anything and tells you which component
is missing, rather than letting all 18 suites fail with `Connection refused` several minutes later.
Use `--skip-preflight` to bypass it.

A test file that breaks the naming convention is silently never executed — run `./ci/validator.sh`
before opening a PR. Those checks are ordinary JUnit tests with one entry per file, so a violation
names the offending file in `target/validation-reports`. See [`ci/README.md`](ci/README.md).

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
