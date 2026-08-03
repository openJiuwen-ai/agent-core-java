#!/usr/bin/env bash

set -Eeuo pipefail

origin_url="http://127.0.0.1:8081"
cloudflared_command="cloudflared"
runtime_dir_value=""
wait_seconds=60

usage() {
    cat <<'EOF'
Usage: start-quick-tunnel.sh [options]

Start only a Cloudflare Quick Tunnel for an already-running service.

Options:
  --origin <url>         Local origin URL (default: http://127.0.0.1:8081)
  --cloudflared <path>   cloudflared executable or command name
  --runtime-dir <path>   PID, state, and log directory
  --wait-seconds <n>     Startup timeout for each readiness phase (default: 60)
  -h, --help             Show this help
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
        --origin)
            require_option_value "$1" "${2-}"
            origin_url="$2"
            shift 2
            ;;
        --cloudflared)
            require_option_value "$1" "${2-}"
            cloudflared_command="$2"
            shift 2
            ;;
        --runtime-dir)
            require_option_value "$1" "${2-}"
            runtime_dir_value="$2"
            shift 2
            ;;
        --wait-seconds)
            require_option_value "$1" "${2-}"
            wait_seconds="$2"
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

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

require_command() {
    command -v "$1" >/dev/null 2>&1 ||
        fail "Required command is unavailable: $1"
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
    command -v "$value" 2>/dev/null ||
        fail "Required command is unavailable: $value"
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
    for _ in {1..20}; do
        if ! is_active_pid "$pid"; then
            wait "$pid" 2>/dev/null || true
            return
        fi
        sleep 0.25
    done
    kill -KILL "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}

http_status() {
    local url="$1"
    curl --silent --show-error \
        --output /dev/null \
        --write-out '%{http_code}' \
        --connect-timeout 3 \
        --max-time 5 \
        "$url" 2>/dev/null || true
}

json_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\t'/\\t}"
    printf '%s' "$value"
}

[[ "$origin_url" =~ ^https?://[^/[:space:]]+/?$ ]] ||
    fail "Origin must be an HTTP(S) base URL without a path: $origin_url"
origin_url="${origin_url%/}"
[[ "$wait_seconds" =~ ^[1-9][0-9]*$ ]] ||
    fail "Wait seconds must be a positive integer"

require_command curl
require_command grep
require_command head
require_command nohup
require_command tr
cloudflared_path="$(resolve_executable "$cloudflared_command")"

if [[ -z "$runtime_dir_value" ]]; then
    runtime_dir="$repository_root/examples/gitcode_issue_evolver/.runtime/quick-tunnel"
elif [[ "$runtime_dir_value" == /* ]]; then
    runtime_dir="$runtime_dir_value"
else
    runtime_dir="$repository_root/$runtime_dir_value"
fi
mkdir -p -- "$runtime_dir"
runtime_dir="$(CDPATH= cd -P -- "$runtime_dir" && pwd)"

pid_file="$runtime_dir/cloudflared.pid"
state_file="$runtime_dir/quick-tunnel.json"
tunnel_out="$runtime_dir/cloudflared.out.log"
tunnel_err="$runtime_dir/cloudflared.err.log"

recorded_pid=""
if [[ -f "$pid_file" ]]; then
    recorded_pid="$(tr -d '[:space:]' < "$pid_file")"
fi
if is_active_pid "$recorded_pid"; then
    fail "A recorded Quick Tunnel process is still running with PID $recorded_pid"
fi
rm -f -- "$pid_file" "$state_file"

local_health_url="$origin_url/health/ready"
local_status="$(http_status "$local_health_url")"
[[ "$local_status" == "200" ]] ||
    fail "Local readiness check returned HTTP ${local_status:-000}: $local_health_url"

: > "$tunnel_out"
: > "$tunnel_err"

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
        rm -f -- "$pid_file" "$state_file"
    fi
    exit "$status"
}
trap cleanup_on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

nohup "$cloudflared_path" tunnel \
    --url "$origin_url" \
    --protocol http2 \
    --no-autoupdate \
    > "$tunnel_out" 2> "$tunnel_err" < /dev/null &
tunnel_pid=$!
printf '%s\n' "$tunnel_pid" > "$pid_file"

public_url=""
deadline=$((SECONDS + wait_seconds))
while ((SECONDS < deadline)); do
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
    fail "Cloudflared did not publish a Quick Tunnel URL within ${wait_seconds}s; inspect $tunnel_err"

public_health_url="$public_url/health/ready"
public_status=""
deadline=$((SECONDS + wait_seconds))
while ((SECONDS < deadline)); do
    if ! is_active_pid "$tunnel_pid"; then
        wait "$tunnel_pid" 2>/dev/null || true
        fail "Cloudflared stopped before public readiness succeeded; inspect $tunnel_err"
    fi
    public_status="$(http_status "$public_health_url")"
    if [[ "$public_status" == "200" ]]; then
        break
    fi
    sleep 1
done
[[ "$public_status" == "200" ]] ||
    fail "Public readiness check returned HTTP ${public_status:-000}: $public_health_url"

webhook_url="$public_url/webhooks/gitcode"
state_temp="$state_file.tmp.$$"
printf '{\n' > "$state_temp"
printf '  "tunnelPid": %s,\n' "$tunnel_pid" >> "$state_temp"
printf '  "originUrl": "%s",\n' "$(json_escape "$origin_url")" >> "$state_temp"
printf '  "localHealthUrl": "%s",\n' "$(json_escape "$local_health_url")" >> "$state_temp"
printf '  "publicUrl": "%s",\n' "$(json_escape "$public_url")" >> "$state_temp"
printf '  "publicHealthUrl": "%s",\n' "$(json_escape "$public_health_url")" >> "$state_temp"
printf '  "webhookUrl": "%s"\n' "$(json_escape "$webhook_url")" >> "$state_temp"
printf '}\n' >> "$state_temp"
mv -f -- "$state_temp" "$state_file"
state_temp=""

cleanup_required=false
printf 'Quick Tunnel ready.\n'
printf 'Tunnel PID: %s\n' "$tunnel_pid"
printf 'Local health URL: %s\n' "$local_health_url"
printf 'Public health URL: %s\n' "$public_health_url"
printf 'Webhook URL: %s\n' "$webhook_url"
printf 'State file: %s\n' "$state_file"
printf 'Cloudflared logs: %s and %s\n' "$tunnel_out" "$tunnel_err"
printf 'The URL is temporary and may change whenever cloudflared restarts.\n'
