/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Minimal permission evaluation result.
 *
 * <p>Mirrors Python's {@code PermissionResult} in
 * {@code openjiuwen.harness.security.models}.
 */
public class PermissionResult {

    private final PermissionLevel permission;
    private final String matchedRule;
    private final String reason;
    private final java.util.List<String> externalPaths;

    public PermissionResult(PermissionLevel permission, String matchedRule, String reason) {
        this(permission, matchedRule, reason, java.util.List.of());
    }

    public PermissionResult(PermissionLevel permission, String matchedRule, String reason, java.util.List<String> externalPaths) {
        this.permission = permission;
        this.matchedRule = matchedRule;
        this.reason = reason;
        this.externalPaths = externalPaths != null ? externalPaths : java.util.List.of();
    }

    public PermissionLevel getPermission() {
        return permission;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public String getReason() {
        return reason;
    }

    public java.util.List<String> getExternalPaths() {
        return externalPaths;
    }

    /**
     * Check if permission is ALLOW.
     * <p>Mirrors Python's {@code is_allowed} property.
     */
    public boolean isAllowed() {
        return permission == PermissionLevel.ALLOW;
    }

    /**
     * Check if permission is DENY.
     * <p>Mirrors Python's {@code is_denied} property.
     */
    public boolean isDenied() {
        return permission == PermissionLevel.DENY;
    }

    /**
     * Check if permission requires approval (ASK).
     * <p>Mirrors Python's {@code needs_approval} property.
     */
    public boolean needsApproval() {
        return permission == PermissionLevel.ASK;
    }
}
