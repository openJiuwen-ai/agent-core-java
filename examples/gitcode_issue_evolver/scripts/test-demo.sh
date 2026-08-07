#!/usr/bin/env bash

set -Eeuo pipefail

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

bash "$script_root/build-demo.sh"

runtime_dir="$repository_root/examples/gitcode_issue_evolver/.runtime"
test_classes="$runtime_dir/test-classes"
dependencies="$(tr -d '\r\n' < "$runtime_dir/compile-classpath.txt")"
compile_classpath="$runtime_dir/classes:$repository_root/target/classes:$dependencies"
temporary_classes="$runtime_dir/.test-classes.tmp.$$"

cleanup() {
    rm -rf -- "$temporary_classes"
}
trap cleanup EXIT

rm -rf -- "$temporary_classes"
mkdir -p -- "$temporary_classes"
mapfile -d '' -t test_sources < <(
    find "$repository_root/examples/gitcode_issue_evolver/src/test/java" \
        -type f -name '*.java' -print0 | sort -z
)
((${#test_sources[@]} > 0)) || {
    printf 'Error: no deterministic Example tests were found\n' >&2
    exit 1
}
javac -encoding UTF-8 -parameters -cp "$compile_classpath" \
    -d "$temporary_classes" "${test_sources[@]}"
rm -rf -- "$test_classes"
mv -- "$temporary_classes" "$test_classes"

run_classpath="$test_classes:$compile_classpath"
java -cp "$run_classpath" examples.gitcode_issue_evolver.gitcode.HttpGitCodeClientDeterministicTest
java -cp "$run_classpath" examples.gitcode_issue_evolver.polling.PollingDeterministicTest
java -cp "$run_classpath" examples.gitcode_issue_evolver.ServiceTriggerModeDeterministicTest

printf 'GitCode Issue Evolver deterministic tests passed.\n'
