/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

/**
 * Bash tool permission enforcement mode.
 *
 * <p>Mirrors Python's PermissionMode in
 * {@code openjiuwen.harness.tools.shell.bash._permission}.
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

    public String getValue() {
        return value;
    }

    public static PermissionMode fromString(String value) {
        for (PermissionMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return AUTO;
    }
}