/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;

import java.util.Map;
import java.util.Objects;

/**
 * Top-level workflow chunk alias for streamed workflow outputs.
 *
 * <p>Mirrors Python's {@code WorkflowChunk = Union[OutputSchema, CustomSchema, TraceSchema]} in
 * {@code openjiuwen/core/workflow/workflow.py}.</p>
 */
public class WorkflowChunk extends TraceSchema {

    private static final long serialVersionUID = 1L;

    private int index;

    public WorkflowChunk() {
    }

    public WorkflowChunk(String type, int index, Object payload) {
        super(type, payload);
        this.index = index;
    }

    public static WorkflowChunk from(Object value) {
        if (value instanceof WorkflowChunk workflowChunk) {
            return workflowChunk;
        }
        if (value instanceof OutputSchema outputSchema) {
            return outputSchema;
        }
        if (value instanceof TraceSchema traceSchema) {
            return new WorkflowChunk(traceSchema.getType(), 0, traceSchema.getPayload());
        }
        if (value instanceof Map<?, ?> map && isOutputSchemaMap(map)) {
            return new OutputSchema(
                    stringValue(map.get("type")),
                    intValue(map.get("index")),
                    map.get("payload"));
        }
        return new OutputSchema("output", 0, value);
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkflowChunk that)) {
            return false;
        }
        return index == that.index
                && Objects.equals(getType(), that.getType())
                && Objects.equals(getPayload(), that.getPayload());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), index, getPayload());
    }

    private static boolean isOutputSchemaMap(Map<?, ?> map) {
        return map.containsKey("type") || map.containsKey("payload") || map.containsKey("index");
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
