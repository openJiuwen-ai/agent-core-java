/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Output of a guardrail backend analysis.
 * 
 * @since 0.1.7
 */
@Value
@Builder
public class RiskAssessment {
    boolean hasRisk;
    @Builder.Default
    RiskLevel riskLevel = RiskLevel.SAFE;
    String riskType;
    @Builder.Default
    double confidence = 0.0;
    Map<String, Object> details;
}
