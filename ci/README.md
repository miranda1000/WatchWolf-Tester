# WatchWolf - Tester
## CI scripts

Every script runs Maven inside Docker, so the host needs nothing but Docker itself.

| Script | Does |
| --- | --- |
| `./ci/build.sh [--preclean]` | Build the library jar into `target/` (skips tests) |
| `./ci/tests.sh --unit [--tests <pattern>]` | Run the unit tests |
| `./ci/tests.sh --integration [--tests <pattern>]` | Run the system tests (**needs a live environment**, see below) |
| `./ci/tests.sh --integration --skip-preflight` | ...without first checking that the environment is up |
| `./ci/validator.sh` | Run the code checks (naming conventions, system-test timeouts) |

## The three suites

| | Unit | System / integration | Code checks |
| --- | --- | --- | --- |
| Source root | `src/test/java` | `src/integration-test/java` | `src/validation-test/java` |
| Naming | `*Should` | `IT*` | `*Should` |
| Runner | Surefire | Failsafe | Surefire (separate execution) |
| Maven profile | `default` | `-P integration-test` | `-P validation-test` |
| Reports | `target/surefire-reports` | `target/failsafe-reports` | `target/validation-reports` |
| Needs a WatchWolf environment | no | **yes** | no |

Each suite writes to its own reports directory, so a failing code check is never mixed into the
unit-test report. `target/site` holds the HTML summary.

### Code checks

These assert things about *the repository* rather than about its behaviour — today, the test
naming conventions and the system-test timeouts. They are ordinary JUnit tests, one dynamic test
per file, so a violation reports individually and names the offending file:

```
nameEverySystemTestWithTheItPrefix() generic/BadlyNamedTest.java
  -> BadlyNamedTest.java is under src/integration-test/java but does not start with 'IT',
     so Failsafe will never run it
```

This is also where a linter or static-analysis run would be launched from.

A file that breaks the naming convention is **silently never executed** by Maven, which is how
this repository ended up with a suite nobody had run in years. Run `./ci/validator.sh` before
opening a PR.

### Running the system tests

They drive real Minecraft servers and real clients, so they need a running WatchWolf environment:
a ServersManager on port 8000 and a ClientsManager on port 7000 (see the
[WatchWolf setup script](https://github.com/watch-wolf/WatchWolf)). Each suite reads its own
`resources/config.yaml`, whose `provider` key must point at the machine running that environment.

They are slow, not hermetic, and are excluded from the default build on purpose — `mvn package`
and `./ci/build.sh` must never depend on a live environment.

**The script checks this for you first.** `AbstractTest#beforeAll` opens sockets to *both* managers
before a single assertion runs, even in suites that never use a client, so a missing environment
makes every suite fail identically with `Connection refused` — after minutes of Maven, and with a
report that never mentions the cause. `./ci/tests.sh --integration` probes both ports up front, from
inside the same image and network mode the tests will use, and refuses to run with something
actionable:

```
[e] The WatchWolf environment is not reachable, so every system test would fail with
    'Connection refused' in AbstractTest#beforeAll. Not running them.

      unreachable: 127.0.0.1:8000  (ServersManager)
      unreachable: 127.0.0.1:7000  (ClientsManager)
```

The hosts it probes come from each suite's `resources/*.yaml` (`provider`, defaulting to
`127.0.0.1`), so pointing a suite at another machine is picked up automatically. If the environment
is reachable only from inside the container, pass `--skip-preflight`.

### A note on `@Timeout`

A system test with no `@Timeout` on its class turns a hung server into a hung suite with no
diagnostic. None of the suites inherited from the old flat layout declare one, so that check
currently reports each such file as **skipped, with the reason** rather than failing.

Each file flips to a pass on its own as it gains the annotation. Once none are left, swap the
`assumeTrue` in `SystemTestTimeoutShould` for `assertTrue` to make it fatal, as it already is in
WatchWolf-Core.
