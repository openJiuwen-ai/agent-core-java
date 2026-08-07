#!/usr/bin/env bash

set -Eeuo pipefail

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

if (($# > 0)); then
    fail "build-demo.sh does not accept arguments"
fi

command -v bash >/dev/null 2>&1 || fail "Required command is unavailable: bash"
command -v javac >/dev/null 2>&1 || fail "Required command is unavailable: javac"
command -v find >/dev/null 2>&1 || fail "Required command is unavailable: find"
command -v sort >/dev/null 2>&1 || fail "Required command is unavailable: sort"
command -v tr >/dev/null 2>&1 || fail "Required command is unavailable: tr"

bash "$repository_root/examples/gitcode_issue_evolver/scripts/build-demo.sh"

issue_runtime="$repository_root/examples/gitcode_issue_evolver/.runtime"
feature_root="$repository_root/examples/gitcode_feature_evolver"
runtime_dir="$feature_root/.runtime"
classes_dir="$runtime_dir/classes"
temporary_classes="$runtime_dir/.classes.tmp.$$"
classpath_file="$runtime_dir/compile-classpath.txt"

cleanup() {
    rm -rf -- "$temporary_classes"
}
trap cleanup EXIT

mkdir -p -- "$runtime_dir"
dependencies="$(tr -d '\r\n' < "$issue_runtime/compile-classpath.txt")"
[[ -n "$dependencies" ]] || fail "Issue Example dependency classpath is empty"
compile_classpath="$issue_runtime/classes:$repository_root/target/classes:$dependencies"

mapfile -d '' -t source_files < <(
    find "$feature_root/src/main/java" -type f -name '*.java' -print0 | sort -z
)
((${#source_files[@]} > 0)) || fail "No Feature Evolver Java sources were found"

rm -rf -- "$temporary_classes"
mkdir -p -- "$temporary_classes"
javac -encoding UTF-8 -parameters -cp "$compile_classpath" \
    -d "$temporary_classes" "${source_files[@]}" ||
    fail "GitCode Feature Evolver compilation failed"

rm -rf -- "$classes_dir"
mv -- "$temporary_classes" "$classes_dir"
cp -- "$issue_runtime/compile-classpath.txt" "$classpath_file"

printf 'GitCode Feature Evolver build completed.\n'
printf 'Classes: %s\n' "$classes_dir"
