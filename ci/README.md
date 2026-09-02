# WatchWolf - Tester
## CI scripts

Every script runs Maven inside Docker, so the host needs nothing but Docker itself.

| Script | Does |
| --- | --- |
| `./ci/build.sh [--preclean]` | Build the library jar into `target/` (skips tests) |
| `./ci/tests.sh --unit [--tests <pattern>]` | Run the unit tests |
| `./ci/tests.sh --integration [--tests <pattern>]` | Run the system tests (**needs a live environment**, see below) |
| `./ci/validator.sh` | Check that the test files follow the naming conventions |

Reports land in `target/site` (HTML summary), `target/surefire-reports` (unit) and
`target/failsafe-reports` (integration).

## The two test suites

| | Unit | System / integration |
| --- | --- | --- |
| Source root | `src/test/java` | `src/integration-test/java` |
| Naming | `*Should` | `IT*` |
| Runner | Surefire | Failsafe |
| Maven profile | `default` (active by default) | `-P integration-test` |
| Needs a WatchWolf environment | no | **yes** |

A file that breaks the naming convention is **silently never executed**, which is why
`./ci/validator.sh` exists. Run it before opening a PR.

### Running the system tests

They drive real Minecraft servers and real clients, so they need a running WatchWolf environment:
a ServersManager on port 8000 and a ClientsManager on port 7000 (see the
[WatchWolf setup script](https://github.com/watch-wolf/WatchWolf)). Each suite reads its own
`resources/config.yaml`, whose `provider` key must point at the machine running that environment.

They are slow, not hermetic, and are excluded from the default build on purpose — `mvn package`
and `./ci/build.sh` must never depend on a live environment.

### A note on `@Timeout`

`./ci/validator.sh` currently *warns* when a system test has no `@Timeout` on its class, rather
than failing. A hung server otherwise hangs the whole suite with no diagnostic. WatchWolf-Core
treats the same check as fatal; this repo will follow once the inherited suites declare one.
