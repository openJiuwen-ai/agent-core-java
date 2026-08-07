/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;
import java.util.Optional;

/**
 * Durable authenticated command result.
 *
 * @param status stable result class
 * @param job updated or inspected job
 * @param message safe acknowledgement
 * @since 0.1.12
 */
public record CommandResult(Status status, Optional<FeatureJob> job, String message) {
    /** Validate and freeze the result. */
    public CommandResult {
        status = Objects.requireNonNull(status, "status must not be null");
        job = job == null ? Optional.empty() : job;
        message = message == null ? "" : message;
    }

    /** Command result classes. */
    public enum Status {
        APPLIED,
        STATUS_ONLY,
        ALREADY_SEEN,
        JOB_NOT_FOUND,
        INVALID_FOR_STATE
    }
}
