/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code GuardrailContentType} in
 * {@code openjiuwen/core/security/guardrail/enums.py}.
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

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static GuardrailContentType fromValue(String value) {
        for (GuardrailContentType contentType : values()) {
            if (contentType.value.equals(value)) {
                return contentType;
            }
        }
        throw new IllegalArgumentException("Unknown guardrail content type: " + value);
    }
}
