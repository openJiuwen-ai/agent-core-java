/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureFailure;
import examples.gitcode_feature_evolver.job.FeatureFailureCategory;
import examples.gitcode_feature_evolver.job.FeatureStage;

import java.util.List;
import java.util.Optional;

/** Converts static and container checks into bounded public Gate results. */
final class ApprovedGateResults {
    private ApprovedGateResults() {
    }

    static ApprovedGateReceipt.Result staticValidation(FeatureStage stage,
                                                       List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return passed("Static artifact and contract validation passed");
        }
        String summary = String.join("; ", errors);
        FeatureFailure failure = failure("ARTIFACT_VALIDATION_FAILED",
                FeatureFailureCategory.AGENT_CORRECTABLE, stage,
                "Stage artifacts or immutable contracts failed validation", summary);
        return failed(ApprovedGateReceipt.Status.FAILED, failure, 1, summary);
    }

    static ApprovedGateReceipt.Result container(FeatureStage stage,
                                                 ContainerGateResult gate,
                                                 boolean expectedRed) {
        if (expectedRed ? gate.expectedRed() : gate.passed()) {
            return passed(gate.output());
        }
        return switch (gate.outcome()) {
            case DEPENDENCY_MISSING -> failed(ApprovedGateReceipt.Status.DEPENDENCY_MISSING,
                    failure("MAVEN_DEPENDENCY_MISSING", FeatureFailureCategory.DEPENDENCY_MISSING,
                            stage, "A declared Maven dependency is unavailable offline", gate.output()),
                    gate.exitCode(), gate.output());
            case INFRASTRUCTURE_FAILED, TIMED_OUT -> failed(ApprovedGateReceipt.Status.TRANSIENT,
                    failure("CONTAINER_GATE_TRANSIENT",
                            FeatureFailureCategory.TRANSIENT_INFRASTRUCTURE, stage,
                            "The isolated approved Gate could not complete", gate.outcome().name()),
                    gate.exitCode(), gate.output());
            case BUILD_CONTRACT_FAILED -> failed(ApprovedGateReceipt.Status.FAILED,
                    failure("MAVEN_BUILD_CONTRACT_INVALID",
                            FeatureFailureCategory.CONFIGURATION, stage,
                            "The frozen source Maven version contract is invalid", gate.output()),
                    gate.exitCode(), gate.output());
            case SOURCE_BUILD_FAILED -> failed(ApprovedGateReceipt.Status.FAILED,
                    failure("FROZEN_SOURCE_BUILD_FAILED", FeatureFailureCategory.INTERNAL,
                            stage, "The frozen merged source could not be installed offline",
                            gate.output()), gate.exitCode(), gate.output());
            case UNOBSERVABLE_FAILURE -> failed(ApprovedGateReceipt.Status.FAILED,
                    failure("CONTAINER_GATE_UNOBSERVABLE", FeatureFailureCategory.INTERNAL,
                            stage, "The approved Gate failed without actionable Maven evidence",
                            gate.output()), gate.exitCode(), gate.output());
            case TEST_COMPILATION_FAILED -> failed(ApprovedGateReceipt.Status.FAILED,
                    failure("TEST_COMPILATION_FAILED", FeatureFailureCategory.AGENT_CORRECTABLE,
                            stage, "The Controller-selected test sources did not compile",
                            gate.output()), gate.exitCode(), gate.output());
            case TEST_DISCOVERY_FAILED -> failed(ApprovedGateReceipt.Status.FAILED,
                    failure("TEST_DISCOVERY_FAILED", FeatureFailureCategory.AGENT_CORRECTABLE,
                            stage, "The Controller-selected test classes were not discovered",
                            gate.output()), gate.exitCode(), gate.output());
            case TEST_FAILED, EXPECTED_RED, PASSED -> failed(ApprovedGateReceipt.Status.FAILED,
                    failure(expectedRed ? "RED_EXPECTATION_FAILED" : "TEST_ASSERTION_FAILED",
                            FeatureFailureCategory.AGENT_CORRECTABLE, stage,
                            expectedRed ? "RED did not produce a trustworthy JUnit failure"
                                    : "The Controller-selected test verification failed",
                            gate.output()), gate.exitCode(), gate.output());
        };
    }

    static ApprovedGateReceipt.Result policy(FeatureStage stage, String summary) {
        FeatureFailure failure = failure("WRITE_SCOPE_VIOLATION",
                FeatureFailureCategory.POLICY_VIOLATION, stage,
                "The stage changed a path outside its immutable write scope", summary);
        return failed(ApprovedGateReceipt.Status.FAILED, failure, 1, summary);
    }

    private static ApprovedGateReceipt.Result passed(String output) {
        return new ApprovedGateReceipt.Result(ApprovedGateReceipt.Status.PASSED,
                Optional.empty(), evidence(0, output), false);
    }

    private static ApprovedGateReceipt.Result failed(ApprovedGateReceipt.Status status,
                                                      FeatureFailure failure, int exitCode,
                                                      String output) {
        return new ApprovedGateReceipt.Result(status, Optional.of(failure),
                evidence(exitCode, output), false);
    }

    private static FeatureFailure failure(String code, FeatureFailureCategory category,
                                          FeatureStage stage, String summary, String details) {
        return new FeatureFailure(code, category, stage, stage,
                new FeatureFailure.Diagnostic(summary, details));
    }

    private static ApprovedGateReceipt.Evidence evidence(int exitCode, String output) {
        String sanitized = output == null ? "" : output
                .replaceAll("(?i)(authorization|private-token|access_token)[:=][^\\s]+", "$1=[redacted]")
                .replaceAll("/home/[^/\\s]+", "/home/[redacted]")
                .replaceAll("/var/lib/gitcode-feature-evolver[^\\s]*", "/data/[redacted]");
        return new ApprovedGateReceipt.Evidence(exitCode, sanitized);
    }
}
