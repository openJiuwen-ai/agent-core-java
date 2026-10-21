/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

/**
 * Resolved retry policy for one model client: the max retry count and the
 * per-category switches. Retry is allowed only when the classification says
 * the failure is intrinsically retryable AND the category switch is enabled
 * AND the per-call budget still has retries left; the budget check lives in
 * {@link RetryContext}. Categories without a switch (quota, auth, context
 * length, cancellation) are never retried.
 *
 * @since 2026-10-19
 */
public final class ModelCallRetryPolicy {
    private final int maxRetries;
    private final boolean isRetryOnRateLimit;
    private final boolean isRetryOnTemporary;

    /**
     * Creates a policy.
     *
     * @param maxRetries max retry count per model call, must be >= 0
     * @param isRetryOnRateLimit whether rate limited failures may be retried
     * @param isRetryOnTemporary whether temporary failures may be retried
     */
    public ModelCallRetryPolicy(int maxRetries, boolean isRetryOnRateLimit, boolean isRetryOnTemporary) {
        this.maxRetries = ModelClientConfig.validateMaxRetries(maxRetries);
        this.isRetryOnRateLimit = isRetryOnRateLimit;
        this.isRetryOnTemporary = isRetryOnTemporary;
    }

    /**
     * Resolves the policy from a client configuration.
     *
     * @param config model client configuration, must not be null
     * @return resolved policy
     */
    public static ModelCallRetryPolicy from(ModelClientConfig config) {
        return new ModelCallRetryPolicy(config.getMaxRetries(), config.isRetryOnRateLimit(),
            config.isRetryOnTemporary());
    }

    /**
     * Checks whether the category switch allows retrying this category. Does
     * not consider the intrinsic retryable flag or the remaining budget.
     *
     * @param category failure category, must not be null
     * @return true when the category switch enables retry
     */
    public boolean allows(ModelErrorCategory category) {
        return switch (category) {
            case RATE_LIMITED -> isRetryOnRateLimit;
            case TEMPORARY -> isRetryOnTemporary;
            case QUOTA_EXCEEDED, AUTH_FAILED, USER_CANCELLED, CONTEXT_LENGTH_EXCEEDED -> false;
            default -> false;
        };
    }

    /**
     * Max retry count per model call.
     *
     * @return max retries, never negative
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * Whether rate limited failures may be retried.
     *
     * @return true when enabled
     */
    public boolean isRetryOnRateLimit() {
        return isRetryOnRateLimit;
    }

    /**
     * Whether temporary failures may be retried.
     *
     * @return true when enabled
     */
    public boolean isRetryOnTemporary() {
        return isRetryOnTemporary;
    }
}
