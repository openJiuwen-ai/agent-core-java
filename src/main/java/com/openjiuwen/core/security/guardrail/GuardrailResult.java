/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Final result returned by a guardrail.
 * 
 * @since 0.1.7
 */
@Value
@Builder
public class GuardrailResult {
    boolean isSafe;
    RiskLevel riskLevel;
    String riskType;
    Map<String, Object> details;
    Map<String, Object> modifiedData;

    /**
     * pass.
     * 
     * @param details details
     * @return the result
     * @since 0.1.7
     */
    public static GuardrailResult pass(Map<String, Object> details) {
        return GuardrailResult.builder().isSafe(true).riskLevel(RiskLevel.SAFE).details(details).build();
    }

    /**
     * pass.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static GuardrailResult pass() {
        return pass(null);
    }

    /**
     * block.
     * 
     * @param riskLevel riskLevel
     * @param riskType riskType
     * @param details details
     * @param modifiedData modifiedData
     * @return the result
     * @since 0.1.7
     */
    public static GuardrailResult block(RiskLevel riskLevel, String riskType, Map<String, Object> details,
            Map<String, Object> modifiedData) {
        return GuardrailResult.builder().isSafe(false).riskLevel(riskLevel).riskType(riskType).details(details)
                .modifiedData(modifiedData).build();
    }
}
