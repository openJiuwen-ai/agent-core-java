/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code CallbackMetrics} in
 * {@code openjiuwen/core/runner/callback/models.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackMetrics {

    @Builder.Default
    private int callCount = 0;

    @Builder.Default
    private double totalTime = 0.0;

    @Builder.Default
    private double minTime = Double.POSITIVE_INFINITY;

    @Builder.Default
    private double maxTime = 0.0;

    @Builder.Default
    private int errorCount = 0;

    private Double lastCallTime;

    /**
     * Mirrors Python's {@code update()}.
     *
     * @param executionTime execution time in seconds
     * @param isError whether the callback failed
     */
    public synchronized void update(double executionTime, boolean isError) {
        callCount++;
        totalTime += executionTime;
        minTime = Math.min(minTime, executionTime);
        maxTime = Math.max(maxTime, executionTime);
        lastCallTime = System.currentTimeMillis() / 1000.0;
        if (isError) {
            errorCount++;
        }
    }

    /**
     * Mirrors Python's {@code avg_time} property.
     *
     * @return average execution time, or {@code 0.0} when no calls were recorded
     */
    public double getAvgTime() {
        return callCount > 0 ? totalTime / callCount : 0.0;
    }

    /**
     * Mirrors Python's {@code to_dict()}.
     *
     * @return map containing the Python-style metric keys
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("call_count", callCount);
        result.put("avg_time", getAvgTime());
        result.put("min_time", Double.isInfinite(minTime) ? 0.0 : minTime);
        result.put("max_time", maxTime);
        result.put("error_count", errorCount);
        result.put("error_rate", callCount > 0 ? (double) errorCount / callCount : 0.0);
        result.put("last_call_time", lastCallTime);
        return result;
    }
}
