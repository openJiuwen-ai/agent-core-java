/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Map;

/**
 * Standard output stream schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.base.OutputSchema}.
 */
public class OutputSchema implements WorkflowChunk {

    private String type;
    private int index;
    private Object payload;

    public OutputSchema() {
    }

    public OutputSchema(String type, int index, Object payload) {
        this.type = type;
        this.index = index;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
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
     * @return a validated OutputSchema instance
     */
    @SuppressWarnings("unchecked")
    public static OutputSchema fromMap(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }
        OutputSchema schema = new OutputSchema();
        schema.setType((String) data.get("type"));
        Object idx = data.get("index");
        if (idx instanceof Number) {
            schema.setIndex(((Number) idx).intValue());
        }
        schema.setPayload(data.get("payload"));
        return schema;
    }
}
