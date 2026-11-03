/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

/**
 * Supported tool authentication types.
 *
 * <p>Mirrors Python's {@code AuthType} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public enum AuthType {

    SSL("ssl"),
    HEADER_AND_QUERY("header_and_query");

    private final String value;

    AuthType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AuthType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AuthType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
