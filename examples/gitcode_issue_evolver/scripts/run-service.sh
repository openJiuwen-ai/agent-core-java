#!/usr/bin/env bash

set -Eeuo pipefail

config_file="examples/gitcode_issue_evolver/config/evolver-config.local.json"
secrets_file="examples/gitcode_issue_evolver/config/evolver-secrets.local.json"
model_config="examples/apiconfig.json"
check_only=false

usage() {
    cat <<'EOF'
Usage: run-service.sh [options]

Run the GitCode Issue Evolver Java process in the foreground.

Options:
  --config <path>      Non-secret runtime JSON
  --secrets <path>     Local GitCode Bot and optional webhook secrets JSON
  --llm-config <path>  Model configuration JSON
  --check              Validate configuration, then exit
  -h, --help           Show this help
EOF
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

require_option_value() {
    local option="$1"
    local value="${2-}"
    if [[ -z "$value" || "$value" == --* ]]; then
        fail "Missing value for $option"
    fi
}

while (($# > 0)); do
    case "$1" in
        --config)
            require_option_value "$1" "${2-}"
            config_file="$2"
            shift 2
            ;;
        --secrets)
            require_option_value "$1" "${2-}"
            secrets_file="$2"
            shift 2
            ;;
        --llm-config)
            require_option_value "$1" "${2-}"
            model_config="$2"
            shift 2
            ;;
        --check)
            check_only=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
done

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

resolve_required_file() {
    local value="$1"
    local name="$2"
    local candidate
    if [[ "$value" == /* ]]; then
        candidate="$value"
    else
        candidate="$repository_root/$value"
    fi
    [[ -f "$candidate" && -r "$candidate" ]] ||
        fail "$name file does not exist or is not readable: $candidate"
    local directory
    directory="$(CDPATH= cd -P -- "$(dirname -- "$candidate")" && pwd)"
    printf '%s/%s\n' "$directory" "$(basename -- "$candidate")"
}

command -v java >/dev/null 2>&1 ||
    fail "Required command is unavailable: java"
command -v tr >/dev/null 2>&1 ||
    fail "Required command is unavailable: tr"
command -v uname >/dev/null 2>&1 ||
    fail "Required command is unavailable: uname"

config_path="$(resolve_required_file "$config_file" "Runtime configuration")"
secrets_path="$(resolve_required_file "$secrets_file" "Local secrets")"
model_path="$(resolve_required_file "$model_config" "Model configuration")"

runtime_dir="$repository_root/examples/gitcode_issue_evolver/.runtime"
example_classes="$runtime_dir/classes"
classpath_file="$runtime_dir/compile-classpath.txt"
core_classes="$repository_root/target/classes"
[[ -d "$example_classes" && -d "$core_classes" && -f "$classpath_file" ]] ||
    fail "Example build output is missing; run build-demo.sh first"

dependencies="$(tr -d '\r\n' < "$classpath_file")"
[[ -n "$dependencies" ]] || fail "The Example runtime classpath is empty"
classpath_separator=":"
example_classpath_entry="$example_classes"
core_classpath_entry="$core_classes"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        command -v cygpath >/dev/null 2>&1 ||
            fail "Required command is unavailable: cygpath"
        classpath_separator=";"
        example_classpath_entry="$(cygpath -w "$example_classpath_entry")"
        core_classpath_entry="$(cygpath -w "$core_classpath_entry")"
        ;;
esac
run_classpath="$example_classpath_entry${classpath_separator}${core_classpath_entry}${classpath_separator}${dependencies}"

java_arguments=(
    -cp "$run_classpath"
    examples.gitcode_issue_evolver.GitCodeIssueEvolverExample
    --config "$config_path"
    --secrets "$secrets_path"
    --llm-config "$model_path"
)
if [[ "$check_only" == true ]]; then
    java_arguments+=(--check)
fi

exec java "${java_arguments[@]}"
