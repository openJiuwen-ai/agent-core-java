/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code ContextCompressionState.status} literals in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.
 */
public enum ContextCompressionStatus {
    STARTED("started"),
    COMPLETED("completed"),
    NOOP("noop"),
    SKIPPED("skipped"),
    FAILED("failed");

    private final String value;

    ContextCompressionStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ContextCompressionStatus fromValue(String value) {
        for (ContextCompressionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported context compression status: " + value);
    }
}
