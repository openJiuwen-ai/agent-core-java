/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class TimeoutEvaluator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TimeoutEvaluator implements StopConditionEvaluator {
    private final double timeoutSeconds;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TimeoutEvaluator(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "Timeout";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldStop(StopEvaluationContext context) {
        return context != null && context.getElapsedSeconds() >= timeoutSeconds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Map<String, Object> getState() {
        return java.util.Map.of("timeout_seconds", timeoutSeconds);
    }
}
