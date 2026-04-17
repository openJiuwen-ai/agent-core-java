/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

/**
 * API parameter locations based on OpenAPI specification.
 * <p>
 * Mirrors Python's {@code APIParamLocation} enum.
 */
public enum ApiParamLocation {

    /** Query parameters in URL (e.g., ?key=value). */
    QUERY("query"),

    /** Path parameters in URL (e.g., /users/{id}). */
    PATH("path"),

    /** Request body parameters. */
    BODY("body"),

    /** HTTP header parameters. */
    HEADER("header");

    private final String value;

    ApiParamLocation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a location string (case-insensitive).
     */
    public static ApiParamLocation fromString(String text) {
        for (ApiParamLocation loc : values()) {
            if (loc.value.equalsIgnoreCase(text)) {
                return loc;
            }
        }
        return BODY;
    }
}
