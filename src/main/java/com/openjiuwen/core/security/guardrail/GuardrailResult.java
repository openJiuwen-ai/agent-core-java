/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Final result returned by a guardrail.
 */
@Value
@Builder
public class GuardrailResult {
    boolean isSafe;
    RiskLevel riskLevel;
    String riskType;
    Map<String, Object> details;
    Map<String, Object> modifiedData;

    public static GuardrailResult pass(Map<String, Object> details) {
        return GuardrailResult.builder()
                .isSafe(true)
                .riskLevel(RiskLevel.SAFE)
                .details(details)
                .build();
    }

    public static GuardrailResult pass() {
        return pass(null);
    }

    public static GuardrailResult block(
            RiskLevel riskLevel,
            String riskType,
            Map<String, Object> details,
            Map<String, Object> modifiedData
    ) {
        return GuardrailResult.builder()
                .isSafe(false)
                .riskLevel(riskLevel)
                .riskType(riskType)
                .details(details)
                .modifiedData(modifiedData)
                .build();
    }
}
