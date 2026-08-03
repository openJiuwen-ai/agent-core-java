/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

/**
 * Result of risk analysis from a guardrail backend.
 * <p>
 * Mirrors Python's {@code RiskAssessment} in
 * {@code openjiuwen/core/security/guardrail/models.py}.
 */
@Value
public class RiskAssessment {

    @JsonProperty("has_risk")
    boolean hasRisk;

    @JsonProperty("risk_level")
    RiskLevel riskLevel;

    @JsonProperty("risk_type")
    String riskType;

    @JsonProperty("confidence")
    double confidence;

    @JsonProperty("details")
    Map<String, Object> details;

    public RiskAssessment(
            boolean hasRisk,
            RiskLevel riskLevel,
            String riskType,
            double confidence,
            Map<String, Object> details) {
        this.hasRisk = hasRisk;
        this.riskLevel = riskLevel;
        this.riskType = riskType;
        this.confidence = confidence;
        this.details = details;
    }

    public RiskAssessment(boolean hasRisk, RiskLevel riskLevel) {
        this(hasRisk, riskLevel, null, 0.0d, null);
    }

    public RiskAssessment(boolean hasRisk, RiskLevel riskLevel, String riskType) {
        this(hasRisk, riskLevel, riskType, 0.0d, null);
    }
}
