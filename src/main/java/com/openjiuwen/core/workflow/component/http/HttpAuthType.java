/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

/**
 * HTTP authentication type enum.
 * <p>
 * Mirrors Python's {@code HttpAuthType}.
 *
 * @since 1.0.0
 */
public enum HttpAuthType {
    NONE("none"),
    BASIC("basic"),
    BEARER("bearer"),
    API_KEY("api_key"),
    DIGEST("digest"),
    AWS("aws");

    private final String value;

    HttpAuthType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert a string value to the corresponding HttpAuthType enum.
     *
     * @param value the string value to convert
     * @return the corresponding HttpAuthType, or NONE if not found
     */
    public static HttpAuthType fromValue(String value) {
        for (HttpAuthType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return NONE;
    }
}
