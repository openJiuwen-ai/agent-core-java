/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Map;

/**
 * Mirrors Python's {@code OutputSchema} in
 * {@code openjiuwen/core/session/stream/base.py}.
 */
public class OutputSchema extends WorkflowChunk {

    private static final long serialVersionUID = 1L;

    public OutputSchema() {
    }

    public OutputSchema(String type, int index, Object payload) {
        super(type, index, payload);
    }

    public static OutputSchema fromMap(Map<String, Object> value) {
        if (value == null) {
            return new OutputSchema();
        }
        Object typeValue = value.get("type");
        Object indexValue = value.get("index");
        return new OutputSchema(
                typeValue == null ? null : String.valueOf(typeValue),
                toIndex(indexValue),
                value.get("payload"));
    }

    private static int toIndex(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "OutputSchema{"
                + "type='" + getType() + '\''
                + ", index=" + getIndex()
                + ", payload=" + getPayload()
                + '}';
    }
}
