#!/bin/bash

error=0

script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname "$script_path")
unit_tests_path="$base_path/src/test/java"
integration_tests_path="$base_path/src/integration-test/java"

# are unit tests following naming standard?
warning_files=`find "$unit_tests_path" -type f -name '*.java' ! -name '*Should.java'` # every java file that don't end with "Should"
if [ ! -z "$warning_files" ]; then
    echo "[e] Some files are not following the unit test naming convention. Any unit test that don't end with 'Should' won't run."
    echo "[v] Files that don't follow the convention:"
    echo "$warning_files"
    error=1
fi

# are unit tests following other naming standard?
warning_files=`find "$unit_tests_path" -type f -name 'IT*.java'` # every java file that start with "IT"
if [ ! -z "$warning_files" ]; then
    echo "[e] Some files are not following the unit test naming convention. Any unit test that start with 'IT' won't run."
    echo "[v] Files that don't follow the convention:"
    echo "$warning_files"
    error=1
fi

# are integration tests following naming standard?
warning_files=`find "$integration_tests_path" -type f -name '*.java' ! -name 'IT*.java'` # every java file that don't start with "IT"
if [ ! -z "$warning_files" ]; then
    echo "[e] Some files are not following the system test naming convention. Any system test that don't start with 'IT' won't run."
    echo "[v] Files that don't follow the convention:"
    echo "$warning_files"
    error=1
fi

# does all integration tests have a timeout?
# TODO promote to an error once the suites inherited from src/test/java declare @Timeout
#      (see WatchWolf-Core's ci/validator.sh, where this check is fatal)
candidates=`find "$integration_tests_path" -type f -name 'IT*.java'`
missing_timeout=""
for candidate in $candidates; do
    if [ `grep -Pzc '@Timeout.*\n.*public class' "$candidate"` -eq 0 ]; then
        missing_timeout="$missing_timeout$candidate\n"
    fi
done
if [ ! -z "$missing_timeout" ]; then
    echo "[w] The following system tests don't have a timeout set; a hung server will hang the suite:"
    echo -e "$missing_timeout"
fi

if [ $error -ne 0 ]; then
    exit $error
fi
echo "[i] All done"
