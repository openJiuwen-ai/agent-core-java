/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

/**
 * Classifies failures raised by the controlled local commit boundary.
 *
 * @since 0.1.12
 */
public enum CommitFailureType {
    NONE(false),
    INFRASTRUCTURE(true),
    VALIDATION(false);

    private final boolean retryable;

    CommitFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * Determine whether deployment or local infrastructure repair may make the operation succeed.
     *
     * @return {@code true} for retryable infrastructure failures
     */
    public boolean isRetryable() {
        return retryable;
    }
}
