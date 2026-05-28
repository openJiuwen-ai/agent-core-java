/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

/**
 * Classification of the primary command.
 *
 * <p>Mirrors Python's CommandKind in
 * {@code openjiuwen.harness.tools.shell.bash._semantics}.
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