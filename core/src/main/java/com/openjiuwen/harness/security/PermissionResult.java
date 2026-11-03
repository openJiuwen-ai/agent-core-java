/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.List;

/**
 * Mirrors Python's {@code PermissionResult} in
 * {@code openjiuwen/harness/security/models.py}.
 */
public final class PermissionResult {

    private final PermissionLevel permission;
    private final String matchedRule;
    private final String reason;
    private final List<String> externalPaths;

    public PermissionResult(PermissionLevel permission) {
        this(permission, null, null, null);
    }

    public PermissionResult(PermissionLevel permission, String matchedRule, String reason) {
        this(permission, matchedRule, reason, null);
    }

    public PermissionResult(
            PermissionLevel permission,
            String matchedRule,
            String reason,
            List<String> externalPaths
    ) {
        this.permission = permission;
        this.matchedRule = matchedRule;
        this.reason = reason;
        this.externalPaths = externalPaths == null ? null : List.copyOf(externalPaths);
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

    public List<String> getExternalPaths() {
        return externalPaths;
    }

    public boolean isAllowed() {
        return permission == PermissionLevel.ALLOW;
    }

    public boolean isDenied() {
        return permission == PermissionLevel.DENY;
    }

    public boolean needsApproval() {
        return permission == PermissionLevel.ASK;
    }
}
