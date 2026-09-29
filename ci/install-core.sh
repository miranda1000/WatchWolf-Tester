#!/bin/bash

set -euo pipefail

script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
core_path=$(dirname "$base_path")/WatchWolf-Core
local_maven_repos_path="$HOME/.m2"

core_mount=()
if [[ -f "$core_path/pom.xml" ]]; then core_mount=(-v "$core_path":/core:ro); fi

tty_flags=()
if [[ -t 0 && -t 1 ]]; then tty_flags=(-it); fi

docker run "${tty_flags[@]}" --rm \
    -v "$base_path":/tester:ro -v "$local_maven_repos_path":/root/.m2 \
    "${core_mount[@]}" -w /tmp maven:3.8.4-openjdk-8 bash -euo pipefail -c '
    # Read the dependency version from Tester rather than duplicating it here.
    mvn --batch-mode -q org.apache.maven.plugins:maven-help-plugin:3.4.0:evaluate \
        --file /tester/pom.xml -Dexpression=watchwolf-core.version -Doutput=/tmp/core-version
    core_version=$(cat /tmp/core-version)

    echo "[v] Resolving WatchWolf-Core $core_version from JitPack..."
    if mvn --batch-mode -U org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get \
        -Dartifact="com.github.watch-wolf:WatchWolf-Core:$core_version" \
        -DremoteRepositories=jitpack.io::default::https://jitpack.io -Dtransitive=false; then
        exit 0
    fi

    if [[ ! -f /core/pom.xml ]]; then
        echo "[e] Could not resolve Core from JitPack and no sibling WatchWolf-Core checkout exists." >&2
        exit 1
    fi

    mvn --batch-mode -q org.apache.maven.plugins:maven-help-plugin:3.4.0:evaluate \
        --file /core/pom.xml -Dexpression=project.version -Doutput=/tmp/local-core-version
    local_core_version=$(cat /tmp/local-core-version)
    if [[ "$local_core_version" != "$core_version" ]]; then
        echo "[e] Tester requires Core $core_version, but the sibling checkout is $local_core_version." >&2
        exit 1
    fi

    # Build a temporary copy under the JitPack coordinates, preserving the Core
    # dependency metadata and leaving the sibling checkout untouched.
    echo "[v] JitPack resolution failed; installing sibling WatchWolf-Core $core_version..."
    mkdir /tmp/core
    cp -a /core/pom.xml /core/src /tmp/core/
    # Only replace the first occurrence: dependency coordinates must stay intact.
    sed -i "0,/<groupId>dev.watchwolf<\/groupId>/s//<groupId>com.github.watch-wolf<\/groupId>/" /tmp/core/pom.xml
    sed -i "0,/<artifactId>watchwolf-core<\/artifactId>/s//<artifactId>WatchWolf-Core<\/artifactId>/" /tmp/core/pom.xml
    mvn --batch-mode install -Dmaven.test.skip=true --file /tmp/core/pom.xml
'
