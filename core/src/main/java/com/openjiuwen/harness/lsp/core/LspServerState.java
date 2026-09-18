/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code LspServerState} in
 * {@code openjiuwen/harness/lsp/core/types.py}.
 */
public enum LspServerState {
    STOPPED("stopped"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping"),
    ERROR("error");

    private final String value;

    LspServerState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static LspServerState fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LspServerState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return null;
    }
}
