/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class UsageStats used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class UsageStats {
    private int timesPresented;
    private int timesUsed;
    private int timesPositive;
    private int timesNegative;
    private String lastPresentedAt;
    private String lastEvaluatedAt;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("times_presented", timesPresented);
        payload.put("times_used", timesUsed);
        payload.put("times_positive", timesPositive);
        payload.put("times_negative", timesNegative);
        if (lastPresentedAt != null && !lastPresentedAt.isBlank()) {
            payload.put("last_presented_at", lastPresentedAt);
        }
        if (lastEvaluatedAt != null && !lastEvaluatedAt.isBlank()) {
            payload.put("last_evaluated_at", lastEvaluatedAt);
        }
        return payload;
    }
}
