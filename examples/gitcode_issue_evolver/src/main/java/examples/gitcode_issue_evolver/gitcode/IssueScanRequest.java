/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.time.Instant;
import java.util.Objects;

/**
 * One bounded page request for repository Issues.
 *
 * @param createdAfter inclusive local scan lower bound
 * @param createdBefore inclusive frozen scan upper bound
 * @param label exact GitCode label filter
 * @param page one-based page number
 * @param perPage requested page size
 * @since 0.1.12
 */
public record IssueScanRequest(Instant createdAfter, Instant createdBefore, String label,
                               int page, int perPage) {
    /** Validate one Issue scan page request. */
    public IssueScanRequest {
        Objects.requireNonNull(createdAfter, "createdAfter must not be null");
        Objects.requireNonNull(createdBefore, "createdBefore must not be null");
        if (createdAfter.isAfter(createdBefore)) {
            throw new IllegalArgumentException("createdAfter must not be after createdBefore");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be positive");
        }
        if (perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException("perPage must be between 1 and 100");
        }
    }
}
