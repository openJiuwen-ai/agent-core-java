/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;
import java.util.Optional;

/**
 * Durable feature admission outcome.
 *
 * @param status stable result classification
 * @param job admitted or existing job
 * @since 0.1.12
 */
public record AdmissionResult(Status status, Optional<FeatureJob> job) {
    /** Validate and freeze the outcome. */
    public AdmissionResult {
        status = Objects.requireNonNull(status, "status must not be null");
        job = job == null ? Optional.empty() : job;
    }

    /** Feature admission result classes. */
    public enum Status {
        CREATED,
        ISSUE_ALREADY_ADMITTED,
        DELIVERY_ALREADY_SEEN
    }
}
