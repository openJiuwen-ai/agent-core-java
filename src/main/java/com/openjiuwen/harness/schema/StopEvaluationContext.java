/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.Map;

/**
 * Mirrors Python's {@code StopEvaluationContext} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public final class StopEvaluationContext {

    private final int iteration;
    private final int tokenUsage;
    private final double elapsedSeconds;
    private final Map<String, Object> lastResult;
    private final Map<String, Object> extra;

    public StopEvaluationContext() {
        this(0, 0, 0.0, null, Map.of());
    }

    public StopEvaluationContext(
            int iteration,
            int tokenUsage,
            double elapsedSeconds,
            Map<String, Object> lastResult,
            Map<String, Object> extra
    ) {
        this.iteration = iteration;
        this.tokenUsage = tokenUsage;
        this.elapsedSeconds = elapsedSeconds;
        this.lastResult = lastResult == null ? null : Map.copyOf(lastResult);
        this.extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public int getIteration() {
        return iteration;
    }

    public int getTokenUsage() {
        return tokenUsage;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public Map<String, Object> getLastResult() {
        return lastResult;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}
