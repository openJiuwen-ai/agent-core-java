#!/usr/bin/env bash

set -Eeuo pipefail

config_file="examples/gitcode_issue_evolver/config/evolver-config.local.json"
secrets_file="examples/gitcode_issue_evolver/config/evolver-secrets.local.json"
model_config="examples/apiconfig.json"
cloudflared_command="cloudflared"
skip_build=false

usage() {
    cat <<'EOF'
Usage: start-demo.sh [options]

Options:
  --config <path>       Non-secret runtime JSON
  --secrets <path>      Local GitCode Bot and optional webhook secrets JSON
  --llm-config <path>   Model configuration JSON
  --cloudflared <path>  cloudflared executable or command name
  --skip-build          Reuse existing Example build output
  -h, --help            Show this help
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
        --cloudflared)
            require_option_value "$1" "${2-}"
            cloudflared_command="$2"
            shift 2
            ;;
        --skip-build)
            skip_build=true
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

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command is unavailable: $1"
}

resolve_executable() {
    local value="$1"
    if [[ "$value" == */* ]]; then
        [[ -x "$value" ]] || fail "Executable is unavailable: $value"
        local directory
        directory="$(CDPATH= cd -P -- "$(dirname -- "$value")" && pwd)"
        printf '%s/%s\n' "$directory" "$(basename -- "$value")"
        return
    fi
    command -v "$value" 2>/dev/null || fail "Required command is unavailable: $value"
}

json_integer_field() {
    local file="$1"
    local field="$2"
    local match
    match="$(
        LC_ALL=C tr -d '\r\n' < "$file" |
            grep -oE "\"${field}\"[[:space:]]*:[[:space:]]*[0-9]+" |
            head -n 1 || true
    )"
    if [[ -n "$match" ]]; then
        printf '%s\n' "${match##*:}" | tr -d '[:space:]'
    fi
}

json_string_field() {
    local file="$1"
    local field="$2"
    local match
    match="$(
        LC_ALL=C tr -d '\r\n' < "$file" |
            grep -oE "\"${field}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" |
            head -n 1 || true
    )"
    if [[ -n "$match" ]]; then
        printf '%s\n' "${match#*:}" | sed -E 's/^[[:space:]]*"//; s/"[[:space:]]*$//'
    fi
}

is_active_pid() {
    local pid="${1-}"
    [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

terminate_pid() {
    local pid="${1-}"
    if ! is_active_pid "$pid"; then
        return
    fi
    kill "$pid" 2>/dev/null || true
    for _ in {1..10}; do
        if ! is_active_pid "$pid"; then
            wait "$pid" 2>/dev/null || true
            return
        fi
        sleep 0.2
    done
    kill -KILL "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}

config_path="$(resolve_required_file "$config_file" "Runtime configuration")"
secrets_path="$(resolve_required_file "$secrets_file" "Local secrets")"
model_path="$(resolve_required_file "$model_config" "Model configuration")"

require_command java
require_command curl
require_command grep
require_command nohup
require_command tr
require_command bash
require_command sed

trigger_mode="$(json_string_field "$config_path" "triggerMode")"
trigger_mode="${trigger_mode:-webhook}"
trigger_label="$(json_string_field "$config_path" "triggerLabel")"
trigger_label="${trigger_label:-bug}"
case "$trigger_mode" in
    polling)
        cloudflared_path=""
        ;;
    webhook|both)
        cloudflared_path="$(resolve_executable "$cloudflared_command")"
        ;;
    *)
        fail "triggerMode must be webhook, polling, or both"
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

state_file="$runtime_dir/processes.json"
if [[ -f "$state_file" ]]; then
    old_service_pid="$(json_integer_field "$state_file" "servicePid")"
    old_tunnel_pid="$(json_integer_field "$state_file" "tunnelPid")"
    if is_active_pid "$old_service_pid" || is_active_pid "$old_tunnel_pid"; then
        fail "The demo already has running processes; stop the recorded service and tunnel first"
    fi
    rm -f -- "$state_file"
fi

if [[ "$skip_build" == false ]]; then
    bash "$script_root/build-demo.sh"
fi

service_out="$runtime_dir/service.out.log"
service_err="$runtime_dir/service.err.log"
tunnel_out="$runtime_dir/cloudflared.out.log"
tunnel_err="$runtime_dir/cloudflared.err.log"
: > "$service_out"
: > "$service_err"
: > "$tunnel_out"
: > "$tunnel_err"

service_pid=""
tunnel_pid=""
state_temp=""
cleanup_required=true
cleanup_on_exit() {
    local status=$?
    trap - EXIT
    if [[ -n "$state_temp" ]]; then
        rm -f -- "$state_temp"
    fi
    if [[ "$cleanup_required" == true ]]; then
        terminate_pid "$tunnel_pid"
        terminate_pid "$service_pid"
    fi
    exit "$status"
}
trap cleanup_on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

nohup bash "$script_root/run-service.sh" \
    --config "$config_path" \
    --secrets "$secrets_path" \
    --llm-config "$model_path" \
    > "$service_out" 2> "$service_err" < /dev/null &
service_pid=$!

port="$(json_integer_field "$config_path" "port")"
port="${port:-8081}"
[[ "$port" =~ ^[0-9]+$ ]] && ((port > 0 && port <= 65535)) ||
    fail "Configured port must be between 1 and 65535"
health_url="http://127.0.0.1:$port/health/ready"

ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
    if ! is_active_pid "$service_pid"; then
        wait "$service_pid" 2>/dev/null || true
        fail "The Java service stopped before readiness; inspect $service_err"
    fi
    http_status="$(
        curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
            --max-time 2 "$health_url" 2>/dev/null || true
    )"
    if [[ "$http_status" == "200" ]]; then
        ready=true
        break
    fi
    sleep 1
done
[[ "$ready" == true ]] ||
    fail "The Java service did not become ready; inspect $service_err"

public_url=""
if [[ "$trigger_mode" != "polling" ]]; then
    nohup "$cloudflared_path" tunnel \
        --url "http://127.0.0.1:$port" \
        --protocol http2 \
        --no-autoupdate \
        > "$tunnel_out" 2> "$tunnel_err" < /dev/null &
    tunnel_pid=$!

    for ((attempt = 0; attempt < 60; attempt++)); do
        if ! is_active_pid "$tunnel_pid"; then
            wait "$tunnel_pid" 2>/dev/null || true
            fail "Cloudflared stopped before creating a Quick Tunnel; inspect $tunnel_err"
        fi
        public_url="$(
            grep -Eho 'https://[a-z0-9-]+\.trycloudflare\.com' \
                "$tunnel_out" "$tunnel_err" 2>/dev/null |
                head -n 1 || true
        )"
        if [[ -n "$public_url" ]]; then
            break
        fi
        sleep 1
    done
    [[ -n "$public_url" ]] ||
        fail "Cloudflared did not publish a Quick Tunnel URL; inspect $tunnel_err"
fi

state_temp="$state_file.tmp.$$"
printf '{\n' > "$state_temp"
printf '  "servicePid": %s,\n' "$service_pid" >> "$state_temp"
if [[ -n "$tunnel_pid" ]]; then
    printf '  "tunnelPid": %s,\n' "$tunnel_pid" >> "$state_temp"
else
    printf '  "tunnelPid": null,\n' >> "$state_temp"
fi
printf '  "localHealthUrl": "%s",\n' "$health_url" >> "$state_temp"
printf '  "publicUrl": "%s",\n' "$public_url" >> "$state_temp"
printf '  "triggerMode": "%s",\n' "$trigger_mode" >> "$state_temp"
printf '  "triggerLabel": "%s"\n' "$trigger_label" >> "$state_temp"
printf '}\n' >> "$state_temp"
mv -f -- "$state_temp" "$state_file"
state_temp=""

cleanup_required=false
printf 'Service ready: %s\n' "$health_url"
if [[ "$trigger_mode" == "polling" ]]; then
    printf 'Polling mode is active; no public webhook tunnel was started.\n'
else
    printf 'Webhook URL: %s/webhooks/gitcode\n' "$public_url"
    printf 'Update the GitCode Issue and Pull Request webhook manually.\n'
fi
