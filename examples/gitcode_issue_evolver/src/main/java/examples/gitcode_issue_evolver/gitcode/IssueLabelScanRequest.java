/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

/**
 * One page request for all open repository Issues carrying an exact label.
 *
 * @param label exact GitCode label filter
 * @param page one-based page number
 * @param perPage requested page size
 * @since 0.1.12
 */
public record IssueLabelScanRequest(String label, int page, int perPage) {
    /** Validate one full Issue scan page request. */
    public IssueLabelScanRequest {
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
