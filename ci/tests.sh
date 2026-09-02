#!/bin/bash

# default variables
unit=0
integration=0
test_match=""
skip_preflight=0

# parse params
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --unit) unit=1 ;;
        --integration) integration=1 ;;
        --tests) test_match="$2" ; shift ;;
        --skip-preflight) skip_preflight=1 ;;

        *) echo "[e] Unknown parameter passed: $1" >&2 ; exit 1 ;;
    esac
    shift
done

if [ $integration -eq 0 ] && [ $unit -eq 0 ]; then
    echo "[e] You must specify at least one type of test to run!"
    exit 1
fi

# util variables
script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
local_maven_repos_path="$HOME/.m2"

# docker needs a TTY only when a human is watching; keeping "-it" unconditionally
# breaks these scripts under CI or any non-interactive shell
tty_flags=""
if [ -t 1 ]; then tty_flags="-it"; fi

# The system tests open sockets to the ServersManager and the ClientsManager before running a
# single assertion (AbstractTest#beforeAll). With no environment up, every suite dies with
# "Connection refused" after minutes of Maven, and the report says nothing about the cause.
#
# So check first -- from inside the same image and the same network mode the tests will use, so
# that what we probe is exactly what they will get.
preflight_integration_environment() {
    local providers="" provider endpoints="" probe failures=""

    # each suite reads its own resources/*.yaml; 'provider' defaults to 127.0.0.1 when absent
    while IFS= read -r yaml; do
        provider=$(sed -n 's/^provider:[[:space:]]*//p' "$yaml" | head -1 | tr -d '"'\''\r' | sed 's/[[:space:]]*$//')
        if [ -z "$provider" ]; then provider="127.0.0.1"; fi
        providers="$providers$provider"$'\n'
    done < <(find "$base_path/src/integration-test/java" -name '*.yaml' 2>/dev/null)
    providers=$(printf '%s' "$providers" | sort -u | sed '/^$/d')

    if [ -z "$providers" ]; then
        echo "[w] Couldn't work out which host to check; skipping the preflight."
        return 0
    fi

    for provider in $providers; do
        endpoints="$endpoints $provider:8000 $provider:7000"
    done

    echo "[v] Checking the WatchWolf environment is reachable..."
    probe=$(docker run --rm --network host -e ENDPOINTS="$endpoints" maven:3.8.4-openjdk-8 bash -c '
        for endpoint in $ENDPOINTS; do
            host=${endpoint%:*}; port=${endpoint##*:}
            if timeout 3 bash -c "exec 3<>/dev/tcp/$host/$port" 2>/dev/null; then
                echo "OK $endpoint"
            else
                echo "FAIL $endpoint"
            fi
        done' 2>/dev/null)

    if [ -z "$probe" ]; then
        echo "[w] Couldn't run the preflight (is Docker up?); continuing anyway."
        return 0
    fi

    failures=$(printf '%s\n' "$probe" | grep '^FAIL ' | cut -d' ' -f2)
    if [ -z "$failures" ]; then
        echo "[v] Environment reachable on: $(printf '%s\n' "$probe" | grep '^OK ' | cut -d' ' -f2 | tr '\n' ' ')"
        return 0
    fi

    echo "" >&2
    echo "[e] The WatchWolf environment is not reachable, so every system test would fail with" >&2
    echo "    'Connection refused' in AbstractTest#beforeAll. Not running them." >&2
    echo "" >&2
    for endpoint in $failures; do
        case "${endpoint##*:}" in
            8000) echo "      unreachable: $endpoint  (ServersManager)" >&2 ;;
            7000) echo "      unreachable: $endpoint  (ClientsManager)" >&2 ;;
            *)    echo "      unreachable: $endpoint" >&2 ;;
        esac
    done
    echo "" >&2
    echo "    Both are needed: beforeAll connects to them for every suite, even ones that never" >&2
    echo "    use a client." >&2
    echo "" >&2
    echo "    To start them:  bash WatchWolfSetup.sh --run" >&2
    echo "                    (https://github.com/watch-wolf/WatchWolf)" >&2
    echo "                    or, from a WW-ServersManager checkout, ./ci/release/run.sh" >&2
    echo "" >&2
    echo "    If they run on another machine, set 'provider: \"<host>\"' in the suite's" >&2
    echo "    resources/config.yaml." >&2
    echo "" >&2
    echo "    To run anyway (e.g. the environment is only reachable from inside the container)," >&2
    echo "    pass --skip-preflight." >&2
    echo "" >&2
    return 1
}

# fail fast, before spending minutes on Maven, if the system tests can't possibly pass
if [ $integration -eq 1 ] && [ $skip_preflight -eq 0 ]; then
    preflight_integration_environment || exit 1
fi

# clear
docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8 mvn clean --file '/compile'

if [ $unit -eq 1 ]; then
    unit_tests_report_path="$base_path/target/surefire-reports"
    mkdir -p "$unit_tests_report_path"

    # run unit tests (default profile: unit only)
    if [ ! -z "$test_match" ]; then
        echo "[v] Running filtered tests: $test_match"
        docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8   \
                        mvn test -Dmaven.test.redirectTestOutputToFile=true -Dtest="$test_match" --file '/compile'  \
                2>&1 | tee "$unit_tests_report_path/docker-log.txt" # forward to file
        result=${PIPESTATUS[0]}
    else
        docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8   \
                        mvn test -Dmaven.test.redirectTestOutputToFile=true --file '/compile'                       \
                2>&1 | tee "$unit_tests_report_path/docker-log.txt" # forward to file
        result=${PIPESTATUS[0]}
    fi

    # Convert xml reports into html
    docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.3-openjdk-17 mvn surefire-report:report-only --file '/compile'
    docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.3-openjdk-17 mvn site -DgenerateReports=false --file '/compile'

    if [ $result -ne 0 ]; then
        echo "[e] Unit tests failed"
        exit $result
    fi
fi

if [ $integration -eq 1 ]; then
    # /!\ The system tests drive REAL Minecraft servers and REAL clients against a running
    #     WatchWolf environment (ServersManager on :8000, ClientsManager on :7000). That is what
    #     preflight_integration_environment checks above. See ci/README.md.
    if [ $skip_preflight -eq 1 ]; then
        echo "[w] Preflight skipped; a missing environment will surface as 'Connection refused'."
    fi

    integration_tests_report_path="$base_path/target/failsafe-reports"
    mkdir -p "$integration_tests_report_path"

    if [ ! -z "$test_match" ]; then
        echo "[v] Running filtered tests: $test_match"
        docker run $tty_flags --rm --network host -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8    \
                                mvn test failsafe:integration-test failsafe:verify                                                  \
                                -P integration-test -Dmaven.test.redirectTestOutputToFile=true -Dit.test="$test_match"              \
                                --file '/compile'                                                                                   \
                2>&1 | tee "$integration_tests_report_path/docker-log.txt" # forward to file
        result=${PIPESTATUS[0]}
    else
        docker run $tty_flags --rm --network host -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8    \
                                mvn test failsafe:integration-test failsafe:verify                                                  \
                                -P integration-test -Dmaven.test.redirectTestOutputToFile=true --file '/compile'                    \
                2>&1 | tee "$integration_tests_report_path/docker-log.txt" # forward to file
        result=${PIPESTATUS[0]}
    fi

    # Convert xml reports into html
    docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.3-openjdk-17 mvn surefire-report:failsafe-report-only --file '/compile'
    docker run $tty_flags --rm -v "$base_path":/compile -v "$local_maven_repos_path":/root/.m2 maven:3.8.3-openjdk-17 mvn site -DgenerateReports=false --file '/compile'

    if [ $result -ne 0 ]; then
        echo "[e] Integration tests failed"
        exit $result
    fi
fi

echo "[i] Done"
