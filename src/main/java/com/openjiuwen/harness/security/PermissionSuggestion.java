/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Suggested allow-always rule derived from a concrete tool invocation.
 *
 * <p>Mirrors Python's {@code PermissionSuggestion} in
 * {@code openjiuwen/harness/security/suggestions.py}.</p>
 */
public record PermissionSuggestion(
        String[] tools,
        String matchType,
        String pattern,
        String action,
        String scope,
        String reason
) {

    public PermissionSuggestion(String[] tools, String matchType, String pattern) {
        this(tools, matchType, pattern, "allow", "exact", null);
    }
}
