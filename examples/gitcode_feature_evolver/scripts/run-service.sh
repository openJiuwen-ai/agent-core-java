#!/usr/bin/env bash

set -Eeuo pipefail

config_file="examples/gitcode_feature_evolver/config/feature-config.local.json"
secrets_file="examples/gitcode_feature_evolver/config/feature-secrets.local.json"
model_config="examples/apiconfig.json"
check_only=false
container_test_worktree=""

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

usage() {
    printf '%s\n' \
        'Usage: run-service.sh [options]' \
        '' \
        'Run the independent GitCode Feature Evolver in the foreground.' \
        '' \
        '  --config <path>      Non-secret feature runtime JSON' \
        '  --secrets <path>     Feature bot and Webhook secrets JSON' \
        '  --llm-config <path>  Model configuration JSON' \
        '  --check              Validate all mandatory readiness gates' \
        '  --container-test-worktree <path>' \
        '                       Run the fixed full container gate' \
        '  -h, --help           Show this help'
}

require_value() {
    local option="$1"
    local value="${2-}"
    [[ -n "$value" && "$value" != --* ]] || fail "Missing value for $option"
}

while (($# > 0)); do
    case "$1" in
        --config)
            require_value "$1" "${2-}"
            config_file="$2"
            shift 2
            ;;
        --secrets)
            require_value "$1" "${2-}"
            secrets_file="$2"
            shift 2
            ;;
        --llm-config)
            require_value "$1" "${2-}"
            model_config="$2"
            shift 2
            ;;
        --check)
            check_only=true
            shift
            ;;
        --container-test-worktree)
            require_value "$1" "${2-}"
            container_test_worktree="$2"
            shift 2
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

if [[ "$check_only" == true && -n "$container_test_worktree" ]]; then
    fail "--check and --container-test-worktree are mutually exclusive"
fi

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

resolve_file() {
    local configured="$1"
    local label="$2"
    local candidate
    if [[ "$configured" == /* ]]; then
        candidate="$configured"
    else
        candidate="$repository_root/$configured"
    fi
    [[ -f "$candidate" && -r "$candidate" ]] || fail "$label is unavailable: $candidate"
    local directory
    directory="$(CDPATH= cd -P -- "$(dirname -- "$candidate")" && pwd)"
    printf '%s/%s\n' "$directory" "$(basename -- "$candidate")"
}

command -v java >/dev/null 2>&1 || fail "Required command is unavailable: java"
command -v tr >/dev/null 2>&1 || fail "Required command is unavailable: tr"

config_path="$(resolve_file "$config_file" 'Runtime config')"
secrets_path="$(resolve_file "$secrets_file" 'Secrets file')"
model_path="$(resolve_file "$model_config" 'Model config')"

feature_runtime="$repository_root/examples/gitcode_feature_evolver/.runtime"
issue_runtime="$repository_root/examples/gitcode_issue_evolver/.runtime"
dependencies="$(tr -d '\r\n' < "$feature_runtime/compile-classpath.txt")"
[[ -d "$feature_runtime/classes" && -d "$issue_runtime/classes" \
    && -d "$repository_root/target/classes" && -n "$dependencies" ]] ||
    fail "Build output is missing; run examples/gitcode_feature_evolver/scripts/build-demo.sh"

run_classpath="$feature_runtime/classes:$issue_runtime/classes:$repository_root/target/classes:$dependencies"
arguments=(
    -cp "$run_classpath"
    examples.gitcode_feature_evolver.GitCodeFeatureEvolverExample
    --config "$config_path"
    --secrets "$secrets_path"
    --llm-config "$model_path"
)
if [[ "$check_only" == true ]]; then
    arguments+=(--check)
fi
if [[ -n "$container_test_worktree" ]]; then
    if [[ "$container_test_worktree" != /* ]]; then
        container_test_worktree="$repository_root/$container_test_worktree"
    fi
    [[ -d "$container_test_worktree" ]] ||
        fail "Container test Worktree is unavailable: $container_test_worktree"
    arguments+=(--container-test-worktree "$container_test_worktree")
fi

exec java "${arguments[@]}"
