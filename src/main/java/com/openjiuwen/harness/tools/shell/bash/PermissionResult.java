/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

/**
 * Mirrors Python's {@code PermissionResult} in
 * {@code openjiuwen/harness/tools/shell/bash/_permission.py}.
 */
public class PermissionResult {

    private final boolean allowed;
    private final String reason;

    public PermissionResult(boolean allowed) {
        this(allowed, null);
    }

    public PermissionResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
