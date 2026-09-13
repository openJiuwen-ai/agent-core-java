/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class TimeoutEvaluator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TimeoutEvaluator implements StopConditionEvaluator {
    private final double timeoutSeconds;

    /**
     * TimeoutEvaluator.
     * 
     * @param timeoutSeconds timeoutSeconds
     * @since 0.1.7
     */
    public TimeoutEvaluator(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "Timeout";
    }

    /**
     * shouldStop.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean shouldStop(StopEvaluationContext context) {
        return context != null && context.getElapsedSeconds() >= timeoutSeconds;
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public java.util.Map<String, Object> getState() {
        return java.util.Map.of("timeout_seconds", timeoutSeconds);
    }
}
