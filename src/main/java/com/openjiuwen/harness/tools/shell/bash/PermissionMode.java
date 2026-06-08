/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

/**
 * Mirrors Python's {@code PermissionMode} in
 * {@code openjiuwen/harness/tools/shell/bash/_permission.py}.
 */
public enum PermissionMode {
    AUTO("auto"),
    READ_ONLY("read_only"),
    ACCEPT_EDITS("accept_edits"),
    BYPASS("bypass");

    private final String value;

    PermissionMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
