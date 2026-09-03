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
    @Override public String getConfigFile() { return "src/test/resources/watchwolf.yaml"; }

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
| `startup-timeout` | Seconds to wait for a server to become ready before failing `beforeAll`. Default 300. |

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

src/test/java/             unit tests (*Should) — hermetic, no environment needed
src/integration-test/java/ system tests (IT*) — drive real servers and real bots
src/validation-test/java/  code checks (*Should) — assert the repo's own conventions
ci/                        dockerized build / tests / validator scripts
```

## Build and test

Dockerized scripts, same three verbs as WatchWolf-Core and WatchWolf-ServersManager:

```bash
./ci/build.sh [--preclean]                        # -> target/watchwolf-tester-<version>.jar
./ci/tests.sh --unit [--tests <pattern>]          # Surefire, hermetic
./ci/tests.sh --integration [--tests <pattern>]   # Failsafe, needs a live environment
                                                  #   (preflighted; --skip-preflight to bypass)
./ci/validator.sh                                 # code checks; run before a PR
```

Three suites in three source roots:

| | Unit | System / integration | Code checks |
| --- | --- | --- | --- |
| Source root | `src/test/java` | `src/integration-test/java` | `src/validation-test/java` |
| Naming | `*Should` | `IT*` | `*Should` |
| Runner / profile | Surefire, `default` | Failsafe, `-P integration-test` | Surefire, `-P validation-test` |
| Reports | `target/surefire-reports` | `target/failsafe-reports` | `target/validation-reports` |
| Needs an environment | no | **yes** | no |

`build-helper-maven-plugin` attaches the extra source roots, so all three trees compile on every
build; only *execution* is split by profile. Surefire runs two independent executions
(`default-test` and `validation-tests`) so a failing code check never lands in the unit report.

**The code checks are real tests, not a shell script.** `src/validation-test/java` asserts things
about the repository itself — the naming conventions and the system-test timeouts — as JUnit
`@TestFactory` dynamic tests, one per source file, so a violation reports individually and names
the offending file. `usePhrasedTestCaseMethodName` is set on Surefire, without which every dynamic
test would be recorded under its factory method name and the per-file detail would be lost. This is
where a linter or static-analysis run belongs too.

A file that breaks the naming convention is **silently never executed** by Maven — that is what
these checks catch.

**The system tests are almost the whole suite.** Everything that extends `AbstractTest` starts real
Minecraft servers and real bots against a running environment (ServersManager on 8000, ClientsManager
on 7000), and each reads a `resources/config.yaml` whose `provider` must point at that machine. Those
`resources/` paths are resolved **relative to the project root**, not the classpath, so they move
with their sources.

The unit suite is deliberately a skeleton (`ConfigLoaderShould`, `SocketHelperShould`,
`PositionShould`) — enough to protect the wire codec and the config loader while the socket layer is
reworked, not an attempt at coverage.

**History worth knowing:** until this split, `pom.xml` set `<maven.test.skip>true</maven.test.skip>`,
so the tests were never even *compiled*, let alone run. Two of `ConfigLoaderShould`'s three
assertions had silently rotted, and two `FIXME`s in the unit suite mark real bugs it uncovered
(see `Conventions and gotchas`). Assume anything untested here has drifted.

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

- **Two `FIXME`s in the unit suite are real bugs**, characterised rather than fixed so the split
  stayed reviewable:
  - `TestConfigFileLoader.getConfigFiles()` offsets the *zip* form by `"plugins/"`, but
    ServersManager already resolves offsets against `<server>/plugins`, so such a file lands in
    `plugins/plugins/`. The map form (`"Dir": file`) was fixed in `0a16f0e`; the zip form was not.
  - `Position.getBlock*()` casts to `int`, truncating towards zero. Minecraft block coordinates
    floor, so `x = -0.5` should be block `-1` and currently reports `0`.
- **`SocketData` overrides `equals()` but not `hashCode()`.** `TestConfigFileLoader` keeps plugins
  and config files in `HashSet`s, so their de-duplication is unreliable and set-based comparisons
  in tests are unsafe — compare with `containsAll` instead.

- `entities/blocks/special/**` and `entities/entities/EntityType.java` are **generated** by
  [WatchWolf-MaterialGetter](https://github.com/miranda1000/WatchWolf-MaterialGetter). They
  carry a "do not modify" header. Adding a block property is a multi-repo procedure documented in
  that repo's README, ending in `entities/blocks/BlockReader` here.
- The wire protocol is **hand-written**: `TesterConnector` builds and parses packets byte by byte
  against `API/API.tex` in the WatchWolf repo. Adding an operation means changing the spec, this
  connector, and the peer (WatchWolf-Server or WatchWolf-Client) together.
- Ports are currently **hard-coded** in `AbstractTest`: `provider:8000` (Servers Manager) and
  `provider:7000` (Clients Manager), both marked `TODO change port`.
- **Setup failures fail `beforeAll`; they are never swallowed.** `Tester.onServerStart` runs on the
  connector's async thread, so it hands any failure to `setOnSetupFailure`, which `AbstractTest`
  turns into a `ServerSetupException` naming the phase and the address involved. Nothing runs the
  test body against a half-built connector — that is what used to surface as
  `ArrayIndexOutOfBoundsException` on `getClients()[0]` in *user* code. For the same reason
  `getClients()`/`getClientPetition` throw `ClientNotFoundException` rather than hand back an empty
  pool; `TesterConnector.setExpectedClients` is what lets them tell "none configured" from "none
  connected".
- **The async poll loop reads headers through `PacketHeaderReader`.** The short (1 s) timeout only
  covers the wait for the *first* byte; once a header has started it is read to completion. Reading
  both bytes under one timeout and swallowing the exception is what left the stream a byte out of
  phase and produced the intermittent `EOFException`s. An unrecognised header is fatal for that
  connection (`UnexpectedPacketException`) — we cannot drain arguments we cannot count.
- When `provider` is `127.0.0.1`, `AbstractTest` switches the Tester into `IP_WSL_MODIFY` mode,
  which rewrites returned IPs to the local host address — needed because containers report an IP
  the host cannot reach from WSL.
- Synchronisation matters: server and client actions are asynchronous, so tests that depend on
  ordering must go through `synchronize()` (`SynchronizationManager`) — `overrideSync` in the
  config controls the automatic behaviour.
- Test resources include committed `.mp4` recordings and world `.zip`s under
  `src/integration-test/java/generic/resources/`; treat them as fixtures, not as output.

## Git conventions

- **`dev` is the working branch.** Every WatchWolf repo integrates and releases from `dev`.
  `master` (`main` in the WatchWolf standard repo) is downstream of it — never commit there
  directly, and never open a PR against it.
- **One branch per change, named for its kind:** `fix/<topic>` for defects, `feature/<topic>` for
  new work. Branch from `dev`.
- **Always open a PR into `dev`.** Do not push straight to `dev`, even for a one-line change.
