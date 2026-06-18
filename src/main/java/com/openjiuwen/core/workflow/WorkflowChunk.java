/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.Map;

/**
 * Top-level workflow chunk alias for streamed workflow outputs.
 *
 * <p>Mirrors Python's {@code WorkflowChunk = Union[OutputSchema, CustomSchema, TraceSchema]} in
 * {@code openjiuwen/core/workflow/workflow.py}.</p>
 */
public class WorkflowChunk extends OutputSchema {

    public WorkflowChunk() {
    }

    public WorkflowChunk(String type, int index, Object payload) {
        super(type, index, payload);
    }

    public static WorkflowChunk from(Object value) {
        if (value instanceof WorkflowChunk workflowChunk) {
            return workflowChunk;
        }
        if (value instanceof OutputSchema outputSchema) {
            return new WorkflowChunk(outputSchema.getType(), outputSchema.getIndex(), outputSchema.getPayload());
        }
        if (value instanceof Map<?, ?> map) {
            return new WorkflowChunk(
                    stringValue(map.get("type")),
                    intValue(map.get("index")),
                    map.get("payload"));
        }
        return new WorkflowChunk("output", 0, value);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            return Integer.parseInt(String.valueOf(value));
        }
        return 0;
    }
}
