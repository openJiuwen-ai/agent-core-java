/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Base class for model output parsers.
 * 
 * Converts raw model output to RiskAssessment.
 * Users can implement custom parsers by inheriting from this class.
 * 
 * Mirrors Python's openjiuwen.core.security.guardrail.context.ModelOutputParser
 */
public interface ModelOutputParser {
    
    /**
     * Parse model output to RiskAssessment.
     * 
     * @param modelOutput Raw model output (format varies by model)
     * @return RiskAssessment with risk level and details
     */
    RiskAssessment parse(Object modelOutput);
}