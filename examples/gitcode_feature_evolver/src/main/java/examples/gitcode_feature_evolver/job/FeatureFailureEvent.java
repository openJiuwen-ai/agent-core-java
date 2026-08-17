/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;

/** Durable repair/failure history used to rebuild context after restart. */
public record FeatureFailureEvent(long id, String jobId, RepairAttempt attempt,
                                  FeatureFailure failure, long createdAt) {
    /** Validate the event. */
    public FeatureFailureEvent {
        jobId = Objects.requireNonNull(jobId, "jobId must not be null");
        attempt = Objects.requireNonNull(attempt, "attempt must not be null");
        failure = Objects.requireNonNull(failure, "failure must not be null");
    }

    /** @param tier PRIMARY, DIAGNOSTIC, RETRY, PREFETCH, or FAILURE @param number attempt number */
    public record RepairAttempt(String tier, int number) {
        /** Normalize the attempt. */
        public RepairAttempt {
            tier = tier == null ? "" : tier.strip();
            if (tier.isEmpty() || number < 0) {
                throw new IllegalArgumentException("failure attempt is invalid");
            }
        }
    }
}
