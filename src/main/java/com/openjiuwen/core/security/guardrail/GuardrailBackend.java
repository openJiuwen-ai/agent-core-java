/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.Map;

/**
 * Pluggable backend for risk analysis.
 */
@FunctionalInterface
public interface GuardrailBackend {

    /**
     * Analyze event data and return a risk assessment.
     */
    RiskAssessment analyze(Map<String, Object> data) throws Exception;
}
