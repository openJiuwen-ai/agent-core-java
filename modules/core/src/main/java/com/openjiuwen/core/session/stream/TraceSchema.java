/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Map;

/**
 * Trace stream schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.base.TraceSchema}.
 * 
 * @since 0.1.7
 */
public class TraceSchema implements WorkflowChunk {
    private String type;
    private Object payload;

    /**
     * TraceSchema.
     * 
     * @since 0.1.7
     */
    public TraceSchema() {
    }

    /**
     * TraceSchema.
     * 
     * @param type type
     * @param payload payload
     * @since 0.1.7
     */
    public TraceSchema(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    /**
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getType() {
        return type;
    }

    /**
     * setType.
     * 
     * @param type type
     * @since 0.1.7
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * getPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * setPayload.
     * 
     * @param payload payload
     * @since 0.1.7
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * Validate data from a map.
     * 
     * @param data the data map
     * @return a validated TraceSchema instance
     * @since 0.1.7
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
