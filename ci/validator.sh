#!/bin/bash

# Runs the "code checks" suite: the tests under src/validation-test/java that assert things about
# the repository itself rather than about its behaviour (today, the test naming conventions and the
# system-test timeouts). This is where a linter or a static-analysis run would be launched from too.
#
# They are real JUnit tests, so every check reports individually — one entry per offending file —
# into target/validation-reports.

script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
local_maven_repos_path="$HOME/.m2"

# docker needs a TTY only when a human is watching; keeping "-it" unconditionally
# breaks these scripts under CI or any non-interactive shell
tty_flags=""
if [ -t 1 ]; then tty_flags="-it"; fi

validation_reports_path="$base_path/target/validation-reports"
mkdir -p "$validation_reports_path"

docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8  \
                mvn test -P validation-test -Dmaven.test.redirectTestOutputToFile=true --file '/compile'          \
        2>&1 | tee "$validation_reports_path/docker-log.txt" # forward to file
result=${PIPESTATUS[0]}

if [ $result -ne 0 ]; then
    echo "[e] Code checks failed; see $validation_reports_path"
    exit $result
fi

echo "[i] All done"
