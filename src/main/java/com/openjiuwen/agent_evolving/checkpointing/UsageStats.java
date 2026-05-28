/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.util.HashMap;
import java.util.Map;

/**
 * Usage tracking for an evolution experience.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.types.UsageStats}.
 */
public class UsageStats {

    private int timesPresented = 0;
    private int timesUsed = 0;
    private int timesPositive = 0;
    private int timesNegative = 0;
    private String lastPresentedAt;
    private String lastEvaluatedAt;

    public UsageStats() {
    }

    public UsageStats(int timesPresented, int timesUsed, int timesPositive, int timesNegative,
                      String lastPresentedAt, String lastEvaluatedAt) {
        this.timesPresented = timesPresented;
        this.timesUsed = timesUsed;
        this.timesPositive = timesPositive;
        this.timesNegative = timesNegative;
        this.lastPresentedAt = lastPresentedAt;
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("times_presented", timesPresented);
        payload.put("times_used", timesUsed);
        payload.put("times_positive", timesPositive);
        payload.put("times_negative", timesNegative);
        if (lastPresentedAt != null) {
            payload.put("last_presented_at", lastPresentedAt);
        }
        if (lastEvaluatedAt != null) {
            payload.put("last_evaluated_at", lastEvaluatedAt);
        }
        return payload;
    }

    public static UsageStats fromDict(Map<String, Object> data) {
        return new UsageStats(
                getInt(data, "times_presented", 0),
                getInt(data, "times_used", 0),
                getInt(data, "times_positive", 0),
                getInt(data, "times_negative", 0),
                getString(data, "last_presented_at"),
                getString(data, "last_evaluated_at")
        );
    }

    private static int getInt(Map<String, Object> data, String key, int defaultVal) {
        Object val = data.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    private static String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    // Getters and setters
    public int getTimesPresented() { return timesPresented; }
    public void setTimesPresented(int timesPresented) { this.timesPresented = timesPresented; }
    public int getTimesUsed() { return timesUsed; }
    public void setTimesUsed(int timesUsed) { this.timesUsed = timesUsed; }
    public int getTimesPositive() { return timesPositive; }
    public void setTimesPositive(int timesPositive) { this.timesPositive = timesPositive; }
    public int getTimesNegative() { return timesNegative; }
    public void setTimesNegative(int timesNegative) { this.timesNegative = timesNegative; }
    public String getLastPresentedAt() { return lastPresentedAt; }
    public void setLastPresentedAt(String lastPresentedAt) { this.lastPresentedAt = lastPresentedAt; }
    public String getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(String lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
}