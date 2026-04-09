  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance metrics for callback execution.
 * <p>
 * Tracks execution statistics including call counts, timing, and errors.
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
    private double minTime = Double.MAX_VALUE;

    @Builder.Default
    private double maxTime = 0.0;

    @Builder.Default
    private int errorCount = 0;

    private Double lastCallTime;

    /**
     * Update metrics with new execution data.
     *
     * @param executionTime Time taken for execution in seconds
     * @param isError       Whether the execution resulted in an error
     */
    public synchronized void update(double executionTime, boolean isError) {
        callCount++;
        totalTime += executionTime;
        minTime = Math.min(minTime, executionTime);
        maxTime = Math.max(maxTime, executionTime);
        lastCallTime = (double) System.currentTimeMillis() / 1000.0;
        if (isError) {
            errorCount++;
        }
    }

    /**
     * Calculate average execution time.
     *
     * @return Average execution time in seconds, or 0 if no calls
     */
    public double getAvgTime() {
        return callCount > 0 ? totalTime / callCount : 0.0;
    }

    /**
     * Convert metrics to dictionary format.
     *
     * @return Map containing all metric values
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new ConcurrentHashMap<>();
        result.put("call_count", callCount);
        result.put("avg_time", getAvgTime());
        result.put("min_time", minTime != Double.MAX_VALUE ? minTime : 0.0);
        result.put("max_time", maxTime);
        result.put("error_count", errorCount);
        result.put("error_rate", callCount > 0 ? (double) errorCount / callCount : 0.0);
        if (lastCallTime != null) {
            result.put("last_call_time", lastCallTime);
        }
        return result;
    }
}
