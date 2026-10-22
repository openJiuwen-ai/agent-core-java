/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

/**
 * Per-call retry budget shared by every retry window of one model call (the
 * send stage and, for streaming, the first-segment window). A fresh context
 * is created by the client entry point for each logical call, so the total
 * number of retries across all windows never exceeds
 * {@link ModelCallRetryPolicy#maxRetries()}.
 *
 * @since 2026-10-19
 */
public final class RetryContext {
    private final ModelCallRetryPolicy policy;
    private int remainingRetries;
    private int consumedRetries;

    /**
     * Creates a budget for one logical model call.
     *
     * @param policy resolved retry policy, must not be null
     */
    public RetryContext(ModelCallRetryPolicy policy) {
        this.policy = policy;
        this.remainingRetries = policy.maxRetries();
    }

    /**
     * Attempts to consume one retry from the budget.
     *
     * @return true when a retry was available and has been consumed
     */
    public boolean tryConsumeRetry() {
        if (remainingRetries <= 0) {
            return false;
        }
        remainingRetries--;
        consumedRetries++;
        return true;
    }

    /**
     * The resolved policy backing this budget.
     *
     * @return retry policy
     */
    public ModelCallRetryPolicy policy() {
        return policy;
    }

    /**
     * Number of retries consumed so far within this call.
     *
     * @return consumed retry count
     */
    public int consumedRetries() {
        return consumedRetries;
    }

    /**
     * Number of retries still available within this call.
     *
     * @return remaining retry count, never negative
     */
    public int remainingRetries() {
        return remainingRetries;
    }
}
