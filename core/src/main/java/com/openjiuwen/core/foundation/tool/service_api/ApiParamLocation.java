/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

/**
 * Mirrors Python's {@code APIParamLocation} in
 * {@code openjiuwen/core/foundation/tool/service_api/api_param_mapper.py}.
 */
public enum ApiParamLocation {
    QUERY("query"),
    PATH("path"),
    BODY("body"),
    HEADER("header"),
    FORM("form");

    private final String value;

    ApiParamLocation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ApiParamLocation fromString(String rawValue) {
        for (ApiParamLocation location : values()) {
            if (location.value.equalsIgnoreCase(rawValue)) {
                return location;
            }
        }
        throw new IllegalArgumentException("No enum constant for API parameter location: " + rawValue);
    }
}
