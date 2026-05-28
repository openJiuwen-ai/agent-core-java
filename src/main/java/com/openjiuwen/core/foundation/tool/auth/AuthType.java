/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

/**
 * Enumeration of supported authentication types.
 * <p>
 * Mirrors Python's {@code AuthType} enum from
 * <code>foundation/tool/auth/auth_callback.py</code>.
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

    /**
     * Resolve an AuthType from its string value.
     *
     * @param value the string value
     * @return the matching AuthType, or null if not found
     */
    public static AuthType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AuthType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
