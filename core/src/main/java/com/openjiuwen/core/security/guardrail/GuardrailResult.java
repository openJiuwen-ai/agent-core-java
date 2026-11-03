/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

/**
 * Result of guardrail detection.
 * <p>
 * Mirrors Python's {@code GuardrailResult} in
 * {@code openjiuwen/core/security/guardrail/models.py}.
 */
@Value
public class GuardrailResult {

    @JsonProperty("is_safe")
    boolean isSafe;

    @JsonProperty("risk_level")
    RiskLevel riskLevel;

    @JsonProperty("risk_type")
    String riskType;

    @JsonProperty("details")
    Map<String, Object> details;

    @JsonProperty("modified_data")
    Map<String, Object> modifiedData;

    public static GuardrailResult pass_() {
        return pass_(null);
    }

    public static GuardrailResult pass_(Map<String, Object> details) {
        return new GuardrailResult(true, RiskLevel.SAFE, null, details, null);
    }

    public static GuardrailResult block(RiskLevel riskLevel, String riskType) {
        return block(riskLevel, riskType, null, null);
    }

    public static GuardrailResult block(RiskLevel riskLevel, String riskType, Map<String, Object> details) {
        return block(riskLevel, riskType, details, null);
    }

    public static GuardrailResult block(
            RiskLevel riskLevel,
            String riskType,
            Map<String, Object> details,
            Map<String, Object> modifiedData) {
        return new GuardrailResult(false, riskLevel, riskType, details, modifiedData);
    }
}
