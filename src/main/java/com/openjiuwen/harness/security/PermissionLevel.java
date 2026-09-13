/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.Locale;

/**
 * Public enum PermissionLevel used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public enum PermissionLevel {
    ALLOW,
    ASK,
    DENY;

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static PermissionLevel fromValue(Object value) {
        if (value == null) {
            return ALLOW;
        }
        return switch (String.valueOf(value).trim().toLowerCase(Locale.ROOT)) {
            case "allow" -> ALLOW;
            case "ask" -> ASK;
            case "deny" -> DENY;
            default -> ALLOW;
        };
    }
}
