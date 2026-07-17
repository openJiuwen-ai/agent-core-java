/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of atomically accepting a delivery and enqueuing its Issue.
 *
 * @since 0.1.12
 */
public record EnqueueResult(Status status, Optional<EvolutionJob> job) {
    public EnqueueResult(Status status, Optional<EvolutionJob> job) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.job = Objects.requireNonNull(job, "job must not be null");
    }

    /** Delivery enqueue dispositions. */
    public enum Status {
        CREATED,
        DUPLICATE_DELIVERY,
        EXISTING_ACTIVE_JOB
    }
}
