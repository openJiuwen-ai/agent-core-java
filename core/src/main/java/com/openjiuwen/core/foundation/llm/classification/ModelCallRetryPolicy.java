/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.common.logging.Loggers;
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
        int maxRetries = explicitInt(config, "max_retries", config.getMaxRetries(), systemRetries());
        boolean retryOnRateLimit = explicitBoolean(config, "retry_on_rate_limit",
                config.isRetryOnRateLimit(), systemSwitch("openjiuwen.llm.retry.on-rate-limit", true));
        boolean retryOnTemporary = explicitBoolean(config, "retry_on_temporary",
                config.isRetryOnTemporary(), systemSwitch("openjiuwen.llm.retry.on-temporary", true));
        return new ModelCallRetryPolicy(maxRetries, retryOnRateLimit, retryOnTemporary);
    }

    private static int explicitInt(ModelClientConfig config, String key, int configured, int fallback) {
        if (config.isMaxRetriesExplicit()) {
            return configured;
        }
        Object value = config.getExtraFields().get(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
            return ModelClientConfig.validateMaxRetries(parsed);
        } catch (NumberFormatException exception) {
            return configured;
        }
    }

    private static boolean explicitBoolean(ModelClientConfig config, String key, boolean configured,
            boolean fallback) {
        if ((key.equals("retry_on_rate_limit") && config.isRetryOnRateLimitExplicit())
                || (key.equals("retry_on_temporary") && config.isRetryOnTemporaryExplicit())) {
            return configured;
        }
        Object value = config.getExtraFields().get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return "true".equalsIgnoreCase(value.toString());
    }

    private static int systemRetries() {
        String name = "openjiuwen.llm.retry.max-retries";
        String property = System.getProperty(name);
        if (property == null || property.isBlank()) {
            return 2;
        }
        try {
            int parsed = Integer.parseInt(property.trim());
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException exception) {
            Loggers.LLM.debug("System property {} could not be parsed", name);
        }
        Loggers.LLM.warning("Invalid {} value, falling back to default 2 retries", name);
        return 2;
    }

    private static boolean systemSwitch(String name, boolean defaultValue) {
        String property = System.getProperty(name);
        if (property == null || property.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(property.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(property.trim())) {
            return false;
        }
        Loggers.LLM.warning("Invalid {} value, falling back to default {}", name, defaultValue);
        return defaultValue;
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
