/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Map;

/**
 * Trace stream schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.base.TraceSchema}.
 */
public class TraceSchema implements WorkflowChunk {

    private String type;
    private Object payload;

    public TraceSchema() {
    }

    public TraceSchema(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * Validate data from a map.
     *
     * @param data the data map
     * @return a validated TraceSchema instance
     */
    public static TraceSchema fromMap(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }
        TraceSchema schema = new TraceSchema();
        schema.setType((String) data.get("type"));
        schema.setPayload(data.get("payload"));
        return schema;
    }
}
