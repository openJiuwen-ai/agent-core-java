/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Types of content that can be checked by guardrails.
 * 
 * Mirrors Python's openjiuwen.core.security.guardrail.enums.GuardrailContentType
 */
public enum GuardrailContentType {
    TEXT("text"),
    MESSAGES("messages"),
    TOOL_CALL("tool_call"),
    RAW("raw");

    private final String value;

    GuardrailContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}