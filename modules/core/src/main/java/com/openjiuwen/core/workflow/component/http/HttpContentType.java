/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

/**
 * HTTP request body content type enum.
 * <p>
 * Mirrors Python's {@code HttpContentType}.
 * 
 * @since 0.1.7
 */
public enum HttpContentType {
    JSON("json"),
    FORM("form"),
    MULTIPART_FORM("multipart_form"),
    BINARY("binary"),
    TEXT("text"),
    AUTO("auto");

    private final String value;

    HttpContentType(String value) {
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

    /**
     * Convert a string value to the corresponding HttpContentType enum.
     * 
     * @param value the string value to convert
     * @return the corresponding HttpContentType, or JSON if not found
     * @since 0.1.7
     */
    public static HttpContentType fromValue(String value) {
        for (HttpContentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return JSON;
    }
}
