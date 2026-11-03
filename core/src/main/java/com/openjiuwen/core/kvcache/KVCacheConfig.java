/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

/**
 * KVC management is a best-effort optimization. These are whole-action
 * budgets (HTTP, retries and retry backoff), not transport timeouts.
 *
 * <p>Mirrors Python's {@code kv_cache_config.py} constants and
 * {@code kv_cache_runtime_config.py}.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheConfig {
    /** Whole-action budget for messages/tools range actions. */
    public static final double KVC_RANGE_ACTION_TIMEOUT_SECONDS = 1.5;

    /** Whole-action budget for session-level offload/prefetch. */
    public static final double KVC_SESSION_OFFLOAD_PREFETCH_TIMEOUT_SECONDS = 2.0;

    /** Whole-action budget for session-level evict. */
    public static final double KVC_SESSION_EVICT_TIMEOUT_SECONDS = 3.0;

    /** Whole-action budget for terminal cleanup (close/release/Team finalization). */
    public static final double KVC_TERMINAL_CLEANUP_TIMEOUT_SECONDS = 5.0;

    /** Management action attempts: default 1, i.e. no retries. */
    public static final int KVC_MANAGEMENT_MAX_ATTEMPTS = 1;

    private final double actionTimeout;
    private final double evictTimeout;
    private final double closeTimeout;

    /**
     * Default process-local scheduling budgets for session-level KVC actions.
     */
    public KVCacheConfig() {
        this(
                KVC_SESSION_OFFLOAD_PREFETCH_TIMEOUT_SECONDS,
                KVC_SESSION_EVICT_TIMEOUT_SECONDS,
                KVC_TERMINAL_CLEANUP_TIMEOUT_SECONDS
        );
    }

    /**
     * Explicit budgets, mainly for tests.
     *
     * @param actionTimeout prefetch/offload budget in seconds
     * @param evictTimeout evict budget in seconds
     * @param closeTimeout shutdown eviction budget in seconds
     */
    public KVCacheConfig(double actionTimeout, double evictTimeout, double closeTimeout) {
        this.actionTimeout = actionTimeout;
        this.evictTimeout = evictTimeout;
        this.closeTimeout = closeTimeout;
    }

    public double actionTimeout() {
        return actionTimeout;
    }

    public double evictTimeout() {
        return evictTimeout;
    }

    public double closeTimeout() {
        return closeTimeout;
    }

    /**
     * Resolve one explicit whole-action KVC timeout.
     *
     * @param action KV action name
     * @param target KV action target
     * @param timeout explicit timeout override, may be {@code null}
     * @return the timeout in seconds
     */
    public static double resolveKvcActionTimeout(String action, String target, Double timeout) {
        if (timeout != null) {
            return timeout;
        }
        if ("messages".equals(target) || "tools".equals(target)) {
            return KVC_RANGE_ACTION_TIMEOUT_SECONDS;
        }
        if ("evict".equals(action)) {
            return KVC_SESSION_EVICT_TIMEOUT_SECONDS;
        }
        return KVC_SESSION_OFFLOAD_PREFETCH_TIMEOUT_SECONDS;
    }
}
