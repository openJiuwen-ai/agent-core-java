/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

/**
 * Retry policy for upstream HTTP operations.
 * <p>
 * Mirrors Python's {@code RetryPolicy} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.upstream_client}.
 */
public record RetryPolicy(int maxRetries, double backoffBaseSec, double backoffMaxSec) {

    public RetryPolicy() {
        this(2, 0.2, 2.0);
    }

    public double backoffForAttempt(int attempt) {
        if (attempt <= 0) {
            return 0.0;
        }
        double value = backoffBaseSec * Math.pow(2, attempt - 1);
        return Math.max(0.0, Math.min(value, backoffMaxSec));
    }
}
