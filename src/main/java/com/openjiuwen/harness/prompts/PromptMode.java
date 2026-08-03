/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

/**
 * Mirrors Python's {@code PromptMode} in
 * {@code openjiuwen/harness/prompts/builder.py}.
 */
public enum PromptMode {
    FULL("full"),
    MINIMAL("minimal"),
    NONE("none");

    private final String value;

    PromptMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
