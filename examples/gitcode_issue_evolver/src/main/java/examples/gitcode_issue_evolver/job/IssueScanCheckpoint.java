/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable continuation for one frozen polling window.
 *
 * @param repository target repository
 * @param label exact trigger label
 * @param windowStart frozen creation lower bound
 * @param windowEnd frozen creation upper bound
 * @param nextPage next one-based GitCode page
 * @since 0.1.12
 */
public record IssueScanCheckpoint(String repository, String label, Instant windowStart,
                                  Instant windowEnd, int nextPage) {
    /** Validate a durable scan continuation. */
    public IssueScanCheckpoint {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("repository must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("windowStart must not be after windowEnd");
        }
        if (nextPage < 1) {
            throw new IllegalArgumentException("nextPage must be positive");
        }
    }
}
