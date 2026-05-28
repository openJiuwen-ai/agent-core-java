/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

/**
 * Outcome of a permission check.
 *
 * <p>Mirrors Python's PermissionResult in
 * {@code openjiuwen.harness.tools.shell.bash._permission}.
 */
public class PermissionResult {

    private final boolean allowed;
    private final String reason;

    public PermissionResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public PermissionResult(boolean allowed) {
        this.allowed = allowed;
        this.reason = null;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}