#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
    cat <<'EOF'
Usage: build-demo.sh

Compile agent-core-java and the GitCode Issue Evolver Example without running tests.
EOF
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

if (($# > 1)); then
    fail "Expected no arguments"
fi
if (($# == 1)); then
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
fi

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command is unavailable: $1"
}

require_command mvn
require_command javac
require_command find
require_command sort
require_command tr
require_command uname

classpath_separator=":"
core_classpath_entry="$repository_root/target/classes"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        require_command cygpath
        classpath_separator=";"
        core_classpath_entry="$(cygpath -w "$core_classpath_entry")"
        ;;
esac

example_root="$repository_root/examples/gitcode_issue_evolver"
runtime_dir="$example_root/.runtime"
mkdir -p -- "$runtime_dir"
runtime_dir="$(CDPATH= cd -P -- "$runtime_dir" && pwd)"
example_root="$(CDPATH= cd -P -- "$example_root" && pwd)"
case "$runtime_dir/" in
    "$example_root/"*) ;;
    *) fail "Resolved runtime directory escaped the Example directory" ;;
esac

example_classes="$runtime_dir/classes"
classpath_file="$runtime_dir/compile-classpath.txt"
temporary_classes="$runtime_dir/.classes.tmp.$$"
temporary_classpath="$runtime_dir/.compile-classpath.tmp.$$.txt"

cleanup() {
    rm -rf -- "$temporary_classes"
    rm -f -- "$temporary_classpath"
}
trap cleanup EXIT

mvn -B -ntp -Dmaven.test.skip=true compile ||
    fail "Maven compilation failed"

rm -f -- "$temporary_classpath"
mvn -B -ntp -DincludeScope=compile \
    "-Dmdep.outputFile=$temporary_classpath" dependency:build-classpath ||
    fail "Unable to build the Example compilation classpath"

dependencies="$(tr -d '\r\n' < "$temporary_classpath")"
[[ -n "$dependencies" ]] ||
    fail "The Example compilation classpath is empty"
compile_classpath="$core_classpath_entry${classpath_separator}${dependencies}"

rm -rf -- "$temporary_classes"
mkdir -p -- "$temporary_classes"
source_root="$repository_root/examples/gitcode_issue_evolver/src/main/java"
mapfile -d '' -t source_files < <(
    find "$source_root" -type f -name '*.java' -print0 | sort -z
)
shared_loader="$repository_root/examples/utils/SharedExampleApiConfigLoader.java"
[[ -f "$shared_loader" ]] ||
    fail "Shared Example API configuration loader is missing: $shared_loader"
source_files+=("$shared_loader")
((${#source_files[@]} > 1)) ||
    fail "No GitCode Issue Evolver Example sources were found"

javac -encoding UTF-8 -parameters -cp "$compile_classpath" \
    -d "$temporary_classes" "${source_files[@]}" ||
    fail "GitCode Issue Evolver Example compilation failed"

rm -rf -- "$example_classes"
mv -- "$temporary_classes" "$example_classes"
mv -f -- "$temporary_classpath" "$classpath_file"

printf 'GitCode Issue Evolver Example build completed.\n'
printf 'Classes: %s\n' "$example_classes"
printf 'Classpath: %s\n' "$classpath_file"
