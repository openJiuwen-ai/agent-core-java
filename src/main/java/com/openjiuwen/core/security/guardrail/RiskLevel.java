/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.security.guardrail;

/**
 * Risk severity levels for guardrail assessments.
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

    public String getValue() {
        return value;
    }
}
