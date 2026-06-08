/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

/**
 * Write operation mode.
 *
 * <p>Mirrors Python's {@code WriteMode} in {@code openjiuwen/core/memory/lite/conflict_types.py}.</p>
 */
public enum WriteMode {
    CREATE("create"),
    APPEND("append"),
    SKIP("skip");

    private final String value;

    WriteMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
