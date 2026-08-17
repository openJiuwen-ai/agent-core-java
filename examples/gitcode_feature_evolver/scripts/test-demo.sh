#!/usr/bin/env bash

set -Eeuo pipefail

script_root="$(CDPATH= cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(CDPATH= cd -P -- "$script_root/../../.." && pwd)"
cd -- "$repository_root"

bash "$script_root/build-demo.sh"
bash "$script_root/test-logging-policy.sh"

feature_runtime="$repository_root/examples/gitcode_feature_evolver/.runtime"
issue_runtime="$repository_root/examples/gitcode_issue_evolver/.runtime"
test_classes="$feature_runtime/test-classes"
temporary_classes="$feature_runtime/.test-classes.tmp.$$"
dependencies="$(tr -d '\r\n' < "$feature_runtime/compile-classpath.txt")"
compile_classpath="$feature_runtime/classes:$issue_runtime/classes:$repository_root/target/classes:$dependencies"

cleanup() {
    rm -rf -- "$temporary_classes"
}
trap cleanup EXIT

rm -rf -- "$temporary_classes"
mkdir -p -- "$temporary_classes"
mapfile -d '' -t test_sources < <(
    find "$repository_root/examples/gitcode_feature_evolver/src/test/java" \
        -type f -name '*.java' -print0 | sort -z
)
((${#test_sources[@]} > 0)) || {
    printf 'Error: no deterministic Feature Evolver tests were found\n' >&2
    exit 1
}

javac -encoding UTF-8 -parameters -cp "$compile_classpath" \
    -d "$temporary_classes" "${test_sources[@]}"
rm -rf -- "$test_classes"
mv -- "$temporary_classes" "$test_classes"

run_classpath="$test_classes:$compile_classpath"
java -cp "$run_classpath" examples.gitcode_feature_evolver.agent.FeatureStageAgentDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.job.SqliteFeatureJobStoreDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.gitcode.HttpFeatureGitCodeClientDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.polling.FeaturePollingDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.infrastructure.ContainerAndPathPolicyDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.webhook.FeatureWebhookParserDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.workflow.FeatureArtifactInspectorDeterministicTest
java -cp "$run_classpath" \
    examples.gitcode_feature_evolver.workflow.ApprovedGateControllerDeterministicTest
java -cp "$run_classpath" \
    examples.gitcode_feature_evolver.workflow.SystemTestArtifactInspectorDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.worker.FeatureWorkerDeterministicTest
java -cp "$run_classpath" \
    examples.gitcode_feature_evolver.publish.FeaturePullRequestPublisherDeterministicTest
java -cp "$run_classpath" \
    examples.gitcode_feature_evolver.publish.SystemTestPullRequestPublisherDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.FeatureServiceModeDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.FeatureManualPollingDeterministicTest
java -cp "$run_classpath" examples.gitcode_feature_evolver.monitor.FeatureMonitorDeterministicTest

printf 'GitCode Feature Evolver deterministic tests passed.\n'
