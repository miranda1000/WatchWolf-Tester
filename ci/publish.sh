#!/bin/bash
set -euo pipefail

if [[ $# -gt 0 ]]; then
    if [[ $# -eq 1 && ( $1 == --help || $1 == -h ) ]]; then
        echo "Usage: $0"
        echo "Build, run unit tests, and publish to GitHub Packages using Docker."
        echo "Requires Docker and credentials for server 'github' in ~/.m2/settings.xml."
        exit 0
    fi
    echo "[e] Unknown arguments. Usage: $0" >&2
    exit 1
fi

command -v docker >/dev/null 2>&1 || {
    echo "[e] Docker is required to publish WatchWolf-Tester." >&2
    exit 1
}

script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
local_maven_repos_path="$HOME/.m2"

if [[ ! -r "$local_maven_repos_path/settings.xml" ]]; then
    echo "[e] Configure GitHub Packages credentials for server 'github' in ~/.m2/settings.xml first." >&2
    exit 1
fi

tty_flags=()
if [[ -t 0 && -t 1 ]]; then tty_flags=(-it); fi

echo "[v] Publishing WatchWolf-Tester to the repository declared in pom.xml..."
exec docker run "${tty_flags[@]}" --rm \
    -v "$base_path":/compile \
    -v "$local_maven_repos_path":/root/.m2 \
    -w /compile \
    maven:3.8.4-openjdk-8 mvn --batch-mode clean deploy --file /compile/pom.xml
