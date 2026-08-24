/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code ContextCompressionState.phase} literals in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.
 */
public enum ContextCompressionPhase {
    ADD_MESSAGES("add_messages"),
    GET_CONTEXT_WINDOW("get_context_window"),
    ACTIVE_COMPRESS("active_compress");

    private final String value;

    ContextCompressionPhase(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ContextCompressionPhase fromValue(String value) {
        for (ContextCompressionPhase phase : values()) {
            if (phase.value.equals(value)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("Unsupported context compression phase: " + value);
    }
}
