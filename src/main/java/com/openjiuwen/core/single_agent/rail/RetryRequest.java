/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

/**
 * Retry directive produced by exception rails.
 *
 * <p>Mirrors Python's {@code RetryRequest} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public class RetryRequest {
    private double delaySeconds;

    public RetryRequest() {
        this(0.0);
    }

    public RetryRequest(double delaySeconds) {
        this.delaySeconds = Math.max(0.0, delaySeconds);
    }

    public double getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(double delaySeconds) {
        this.delaySeconds = Math.max(0.0, delaySeconds);
    }
}
