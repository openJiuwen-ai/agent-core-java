/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

/**
 * Classification of the primary command.
 *
 * <p>Mirrors Python's {@code CommandKind} in
 * {@code openjiuwen/harness/tools/shell/powershell/_semantics.py}.
 */
public enum CommandKind {
    SEARCH("search"),
    READ("read"),
    LIST("list"),
    NEUTRAL("neutral"),
    SILENT("silent"),
    OTHER("other");

    private final String value;

    CommandKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
