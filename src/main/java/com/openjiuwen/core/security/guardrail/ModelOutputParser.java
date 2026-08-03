/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Converts raw model output into a guardrail risk assessment.
 * <p>
 * Mirrors Python's {@code ModelOutputParser} in
 * {@code openjiuwen/core/security/guardrail/context.py}.
 */
public interface ModelOutputParser {

    RiskAssessment parse(Object modelOutput);
}
