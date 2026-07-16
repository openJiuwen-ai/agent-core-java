/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.Map;

/**
 * Pluggable backend for risk analysis.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface GuardrailBackend {
    /**
     * analyze.
     * 
     * @param data data
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    RiskAssessment analyze(Map<String, Object> data) throws Exception;
}
