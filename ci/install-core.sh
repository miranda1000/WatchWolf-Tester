#!/bin/bash

set -e

script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
core_path=$(dirname "$base_path")/WatchWolf-Core
local_maven_repos_path="$HOME/.m2"

if [ ! -f "$core_path/pom.xml" ]; then
    exit 0
fi

tty_flags=""
if [ -t 1 ]; then tty_flags="-it"; fi

echo "[v] Installing the sibling WatchWolf-Core dependency..."
docker run $tty_flags --rm -v "$core_path":/core -v "$local_maven_repos_path":/root/.m2 \
    maven:3.8.4-openjdk-8 mvn install -Dmaven.test.skip=true --file /core
