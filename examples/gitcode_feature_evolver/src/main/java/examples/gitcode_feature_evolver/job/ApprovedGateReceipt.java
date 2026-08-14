/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;
import java.util.Optional;

/** Durable receipt for a Controller-owned approved gate. */
public record ApprovedGateReceipt(String jobId, FeatureStage stage, Identity identity,
                                  Result result, long completedAt) {
    /** Validate the receipt. */
    public ApprovedGateReceipt {
        jobId = required(jobId, "jobId");
        stage = Objects.requireNonNull(stage, "stage must not be null");
        identity = Objects.requireNonNull(identity, "identity must not be null");
        result = Objects.requireNonNull(result, "result must not be null");
    }

    /** Gate profile, immutable input fingerprint, and bounded selector description. */
    public record Identity(String profile, String fingerprint, String selectorSummary) {
        /** Normalize identity fields. */
        public Identity {
            profile = required(profile, "profile");
            fingerprint = required(fingerprint, "fingerprint");
            selectorSummary = selectorSummary == null ? "" : selectorSummary.strip();
        }
    }

    /** Gate result and sanitized evidence. */
    public record Result(Status status, Optional<FeatureFailure> failure,
                         Evidence evidence, boolean cached) {
        /** Validate and freeze the result. */
        public Result {
            status = Objects.requireNonNull(status, "status must not be null");
            failure = failure == null ? Optional.empty() : failure;
            evidence = Objects.requireNonNull(evidence, "evidence must not be null");
        }

        /** @return a copy marked as served from cache */
        public Result asCached() {
            return new Result(status, failure, evidence, true);
        }
    }

    /** Bounded process/static validation evidence. */
    public record Evidence(int exitCode, String outputTail) {
        private static final int MAX_OUTPUT = 12_000;

        /** Bound the evidence. */
        public Evidence {
            String output = outputTail == null ? "" : outputTail.replace('\r', ' ').strip();
            outputTail = output.substring(0, Math.min(output.length(), MAX_OUTPUT));
        }
    }

    /** Public approved-gate statuses. */
    public enum Status {
        PASSED,
        FAILED,
        TRANSIENT,
        DEPENDENCY_MISSING
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
