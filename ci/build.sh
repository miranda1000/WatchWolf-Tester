#!/bin/bash

# default variables
preclean=0

# parse params
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --preclean) preclean=1 ;;

        *) echo "[e] Unknown parameter passed: $1" >&2 ; exit 1 ;;
    esac
    shift
done

# compile latest WW-Tester
echo "[v] Compiling WatchWolf Tester..."
script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
local_maven_repos_path="$HOME/.m2"

# docker needs a TTY only when a human is watching; keeping "-it" unconditionally
# breaks these scripts under CI or any non-interactive shell
tty_flags=""
if [ -t 1 ]; then tty_flags="-it"; fi

if [ $preclean -eq 1 ]; then
    docker run $tty_flags --rm -v "$base_path":"/compile" -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8 mvn clean --file '/compile' # clean project & launch "clean" phase (if any)
fi

# the tests are run by ci/tests.sh; this only produces the library jar
docker run $tty_flags --rm -v "$base_path":"/compile" -v "$local_maven_repos_path":/root/.m2 maven:3.8.4-openjdk-8 mvn package -Dmaven.test.skip=true --file '/compile'

if [ $? -ne 0 ]; then
    echo "[e] Exception while compiling WW-Tester"
    exit 1
fi
