/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import examples.gitcode_feature_evolver.job.FeatureScanCheckpoint;

import java.util.Objects;

/**
 * One bounded updated-at Issue list request.
 *
 * @param window frozen scan window
 * @param label exact trigger label
 * @param page one-based page
 * @param perPage bounded page size
 * @since 0.1.12
 */
public record FeatureIssueScanRequest(FeatureScanCheckpoint.Window window, String label,
                                      int page, int perPage) {
    /** Validate the request. */
    public FeatureIssueScanRequest {
        window = Objects.requireNonNull(window, "window must not be null");
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException("invalid GitCode pagination");
        }
    }
}
