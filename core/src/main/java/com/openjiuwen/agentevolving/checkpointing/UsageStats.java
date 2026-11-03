/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Usage tracking for an evolution experience.
 *
 * <p>Mirrors Python's {@code UsageStats} in
 * {@code openjiuwen/agent_evolving/checkpointing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UsageStats {

    private int timesPresented;
    private int timesUsed;
    private int timesPositive;
    private int timesNegative;
    private String lastPresentedAt;
    private String lastEvaluatedAt;

    public UsageStats() {
    }

    public UsageStats(
            int timesPresented,
            int timesUsed,
            int timesPositive,
            int timesNegative,
            String lastPresentedAt,
            String lastEvaluatedAt
    ) {
        this.timesPresented = timesPresented;
        this.timesUsed = timesUsed;
        this.timesPositive = timesPositive;
        this.timesNegative = timesNegative;
        this.lastPresentedAt = lastPresentedAt;
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
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
        Map<String, Object> resolved = data == null ? Map.of() : data;
        return new UsageStats(
                intValue(resolved.get("times_presented"), 0),
                intValue(resolved.get("times_used"), 0),
                intValue(resolved.get("times_positive"), 0),
                intValue(resolved.get("times_negative"), 0),
                stringValue(resolved.get("last_presented_at")),
                stringValue(resolved.get("last_evaluated_at"))
        );
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public int getTimesPresented() {
        return timesPresented;
    }

    public void setTimesPresented(int timesPresented) {
        this.timesPresented = timesPresented;
    }

    public int getTimesUsed() {
        return timesUsed;
    }

    public void setTimesUsed(int timesUsed) {
        this.timesUsed = timesUsed;
    }

    public int getTimesPositive() {
        return timesPositive;
    }

    public void setTimesPositive(int timesPositive) {
        this.timesPositive = timesPositive;
    }

    public int getTimesNegative() {
        return timesNegative;
    }

    public void setTimesNegative(int timesNegative) {
        this.timesNegative = timesNegative;
    }

    public String getLastPresentedAt() {
        return lastPresentedAt;
    }

    public void setLastPresentedAt(String lastPresentedAt) {
        this.lastPresentedAt = lastPresentedAt;
    }

    public String getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(String lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }
}
