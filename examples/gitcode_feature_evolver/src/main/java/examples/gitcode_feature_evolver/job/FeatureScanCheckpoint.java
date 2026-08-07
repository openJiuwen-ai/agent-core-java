/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.time.Instant;
import java.util.Objects;

/**
 * Frozen updated-at scan window and next GitCode page.
 *
 * @param repository canonical owner/name
 * @param label exact trigger label
 * @param window immutable scan bounds
 * @param nextPage next one-based page
 * @since 0.1.12
 */
public record FeatureScanCheckpoint(String repository, String label, Window window, int nextPage) {
    /** Validate the checkpoint. */
    public FeatureScanCheckpoint {
        repository = requireText(repository, "repository");
        label = requireText(label, "label");
        window = Objects.requireNonNull(window, "window must not be null");
        if (nextPage < 1) {
            throw new IllegalArgumentException("nextPage must be positive");
        }
    }

    /**
     * Immutable scan bounds.
     *
     * @param start inclusive updated-at lower bound
     * @param end inclusive updated-at upper bound
     * @since 0.1.12
     */
    public record Window(Instant start, Instant end) {
        /** Validate chronological bounds. */
        public Window {
            start = Objects.requireNonNull(start, "start must not be null");
            end = Objects.requireNonNull(end, "end must not be null");
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("scan window start must not be after end");
            }
        }
    }

    private static String requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
