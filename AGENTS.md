# AGENTS.md — WatchWolf-Tester

The entry point to WatchWolf and the library plugin authors depend on. You extend `AbstractTest`,
point it at a YAML file describing the servers and players you want, and write ordinary JUnit 5
tests; the Tester orchestrates the Servers Manager, the Clients Manager, the servers and the bots,
then tears everything down.

`dev.watchwolf:watchwolf-tester` · **Java 8** · Maven · published to GitHub Packages
(`maven.pkg.github.com/miranda1000/watchwolf-tester`).

This repo **also still carries the legacy shared library** (`dev.watchwolf.entities.*`) — see
[Two roles](#two-roles).

## Writing a test

```java
@ExtendWith(WorldInteractionPetitionsShould.class)
public class WorldInteractionPetitionsShould extends AbstractTest {
    @Override public String getConfigFile() { return "src/test/java/generic/resources/config.yaml"; }

    @ParameterizedTest
    @ArgumentsSource(WorldInteractionPetitionsShould.class)
    public void breakBlock(TesterConnector connector) throws Exception {
        String username = connector.getClients()[0];
        ExtendedClientPetition client = connector.getClientPetition(username);
        Position target = client.getPosition().add(0, -1, 0);

        client.breakBlock(target);
        assertEquals(Blocks.AIR, connector.server.getBlock(target));
    }
}
```

`AbstractTest` is simultaneously a `BeforeAllCallback`/`AfterAllCallback` (start and stop the
servers), an `ArgumentsProvider` (one `TesterConnector` argument per server type × version) and a
`TestWatcher` (report results, save recordings). Hence the `@ExtendWith(Self.class)` +
`@ArgumentsSource(Self.class)` pair on every test class — that is the required idiom, not
boilerplate you can drop.

`TesterConnector` is the single façade: `connector.server.*` for server petitions,
`connector.getClientPetition(name)` for a bot, `connector.getClients()` for the usernames.

### Config file keys

| Key | Meaning |
| --- | --- |
| `provider` | Host running the Servers/Clients Managers. Defaults to localhost. |
| `server-type` | List of `Type: [versions…]` maps, e.g. `Spigot: ["1.19", "1.8.8"]`. Any folder present in the ServersManager's `server-types/` is valid, including custom ones. |
| `users` | Bot usernames to spawn and whitelist. |
| `plugin` | The plugin under test — a usual-plugin name, a local path, or a URL. |
| `extra-plugins` | Additional plugins, same three forms. |
| `maps` | `"<world>": "<zip>"` — expanded into the server's world folder. |
| `config-files` | A zip expanded into `plugins/`, or `"<dir>": "<file>"` for a single file. |
| `world-type`, `seed` | World generation (`FLAT` by default in the generated `server.properties`). |
| `difficulty`, `invincible` | Initial difficulty; whether player damage is cancelled. |
| `timings-directory`, `recordings-directory` | Where to save timings reports / client videos. |

See `src/test/java/config/resources/complex.yaml` for a worked example of all of them.

## Layout

```
src/main/java/dev/watchwolf/
├── tester/          Tester, TesterConnector, AbstractTest, TestConfigFileLoader,
│                    ClientSocket / ExtendedClientSocket, SynchronizationManager
├── entities/        LEGACY shared domain model (blocks incl. 562 generated classes, entities,
│                    items, files, Position, Version, SocketData, SocketHelper)
├── server/          ServerPetition, BaseServerPetition, WorldGuardServerPetition,
│                    EnhancedInformationServerPetition
├── serversmanager/  ServerManagerPetition, ServerStartNotifier, ServerErrorNotifier
├── client/          ClientPetition, MessageNotifier
└── clientsmanager/  ClientManagerPetition

src/test/java/       the framework's own tests — these are INTEGRATION tests (see below)
```

## Build and test

Plain Maven — this repo has **no `ci/` scripts and no Surefire/Failsafe configuration**, unlike
WatchWolf-Core and WatchWolf-ServersManager. `pom.xml` configures only the compiler plugin, so
`mvn package` produces a thin `target/watchwolf-tester-<version>.jar` with no bundled
dependencies.

```bash
mvn clean package -Dmaven.test.skip=true    # build the library jar
mvn test -Dtest=ConfigLoaderShould           # run one class
```

**A bare `mvn test` runs nothing.** The test classes are named `*Should.java` and `*Tester.java`,
and neither matches Surefire's default includes (`Test*`, `*Test`, `*Tests`, `*TestCase`). Run
them from the IDE, or name the class explicitly with `-Dtest=`.

**And most of `src/test/java` is not a unit-test suite anyway.** `generic/`, `world/`,
`worldguard/`, `timings/`, `server_starter/`, `client/` and `plugin_downloader/` all start real
Minecraft servers and real bots through a running WatchWolf environment (see the WatchWolf repo's
`WatchWolfSetup.sh`), and each reads a `resources/config.yaml` whose `provider` must point at that
machine. `config/ConfigLoaderShould` and `versions/CompatibilityCheckerShould` are the ones that
run standalone.

## Conventions and gotchas

### Two roles

1. **The Tester library** — what plugin authors depend on.
2. **The legacy shared library** — `dev.watchwolf.entities.*` and the `server`/`serversmanager`/
   `client` petition interfaces. WatchWolf-Server links against `watchwolf-tester-0.2.1.jar` for
   exactly this. Its replacement is
   [WatchWolf-Core](https://github.com/watch-wolf/WatchWolf-Core) (`dev.watchwolf.core.*`), which
   the ServersManager already uses. **The two trees are near-duplicates** — `entities/blocks/`,
   `entities/entities/` and `entities/items/` here mirror `core/entities/…` there, down to the
   same 562 generated block classes. A change to an entity usually has to be made twice; check
   both before assuming a fix is complete.

### Everything else

- `entities/blocks/special/**` and `entities/entities/EntityType.java` are **generated** by
  [WatchWolf-MaterialGetter](https://github.com/miranda1000/WatchWolf-MaterialGetter). They
  carry a "do not modify" header. Adding a block property is a multi-repo procedure documented in
  that repo's README, ending in `entities/blocks/BlockReader` here.
- The wire protocol is **hand-written**: `TesterConnector` builds and parses packets byte by byte
  against `API/API.tex` in the WatchWolf repo. Adding an operation means changing the spec, this
  connector, and the peer (WatchWolf-Server or WatchWolf-Client) together.
- Ports are currently **hard-coded** in `AbstractTest`: `provider:8000` (Servers Manager) and
  `provider:7000` (Clients Manager), both marked `TODO change port`.
- When `provider` is `127.0.0.1`, `AbstractTest` switches the Tester into `IP_WSL_MODIFY` mode,
  which rewrites returned IPs to the local host address — needed because containers report an IP
  the host cannot reach from WSL.
- Synchronisation matters: server and client actions are asynchronous, so tests that depend on
  ordering must go through `synchronize()` (`SynchronizationManager`) — `overrideSync` in the
  config controls the automatic behaviour.
- Test resources include committed `.mp4` recordings and world `.zip`s under
  `src/test/java/generic/resources/`; treat them as fixtures, not as output.

## Git conventions

- **`dev` is the working branch.** Every WatchWolf repo integrates and releases from `dev`.
  `master` (`main` in the WatchWolf standard repo) is downstream of it — never commit there
  directly, and never open a PR against it.
- **One branch per change, named for its kind:** `fix/<topic>` for defects, `feature/<topic>` for
  new work. Branch from `dev`.
- **Always open a PR into `dev`.** Do not push straight to `dev`, even for a one-line change.
