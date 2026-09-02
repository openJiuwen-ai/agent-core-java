/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Risk severity levels for guardrail assessments.
 * 
 * @since 0.1.7
 */
public enum RiskLevel {
    SAFE("safe"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String value;

    RiskLevel(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
