/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

/**
 * HTTP response format enum.
 * <p>
 * Mirrors Python's {@code HttpResponseFormat}.
 * 
 * @since 0.1.7
 */
public enum HttpResponseFormat {
    AUTODETECT("autodetect"),
    JSON("json"),
    TEXT("text"),
    BINARY("binary"),
    BUFFER("buffer");

    private final String value;

    HttpResponseFormat(String value) {
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
     * Convert a string value to the corresponding HttpResponseFormat enum.
     * 
     * @param value the string value to convert
     * @return the corresponding HttpResponseFormat, or AUTODETECT if not found
     * @since 0.1.7
     */
    public static HttpResponseFormat fromValue(String value) {
        for (HttpResponseFormat format : values()) {
            if (format.value.equalsIgnoreCase(value)) {
                return format;
            }
        }
        return AUTODETECT;
    }
}
