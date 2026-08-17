#!/usr/bin/env bash

set -Eeuo pipefail

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
logging_root="$script_root/../deploy/logging"
safe_config="$logging_root/logback-safe.xml"
transcript_template="$logging_root/logback-transcript.xml.template"
unit_file="$script_root/../deploy/systemd/gitcode-feature-evolver.service"
helper="$script_root/../deploy/sbin/manage-feature-evolver-transcript"
example_config="$script_root/../config/feature-config.linux.example.json"

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

(($# == 0)) || fail "test-logging-policy.sh does not accept arguments"

for category in llm agent tool prompt prompt_builder; do
    grep -Fq "<logger name=\"$category\" level=\"OFF\" additivity=\"false\" />" \
        "$safe_config" || fail "Safe policy does not disable $category content"
    grep -Fq "<logger name=\"$category\" level=\"INFO\" additivity=\"false\">" \
        "$transcript_template" || fail "Transcript policy does not capture $category content"
done

grep -Fq '<appender-ref ref="TRANSCRIPT" />' "$transcript_template" ||
    fail "Transcript appender is not referenced"
grep -Fq '<appender-ref ref="CONSOLE" />' "$safe_config" ||
    fail "Safe policy does not retain operational console output"
grep -Fq '@@TRANSCRIPT_FILE@@' "$transcript_template" ||
    fail "Transcript template is missing its fixed output placeholder"
grep -Fq 'RollingFileAppender' "$transcript_template" ||
    fail "Default full transcript is not size-rotated"
grep -Fq '<totalSizeCap>2GB</totalSizeCap>' "$transcript_template" ||
    fail "Default full transcript has no bounded retained-size policy"
grep -Fq 'manage-feature-evolver-transcript apply-config' "$unit_file" ||
    fail "Systemd does not apply the configured startup transcript policy"
grep -Fq 'fullAgentTranscriptEnabled' "$helper" ||
    fail "Transcript helper does not consume the runtime setting"
grep -Eq '"fullAgentTranscriptEnabled"[[:space:]]*:[[:space:]]*true' "$example_config" ||
    fail "Linux exploration configuration does not enable full transcripts"
if grep -Fq 'RUN_LOG' "$safe_config" || grep -Fq 'RUN_LOG' "$transcript_template"; then
    fail "Feature logging policy duplicated journald output into the shared run log"
fi

printf 'Feature Evolver logging policy tests passed.\n'
