/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Map;
import java.util.Objects;

/**
 * Standard output stream schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.base.OutputSchema}.
 * 
 * @since 0.1.7
 */
public class OutputSchema implements WorkflowChunk {
    private String type;
    private int index;
    private Object payload;

    /**
     * OutputSchema.
     * 
     * @since 0.1.7
     */
    public OutputSchema() {
    }

    /**
     * OutputSchema.
     * 
     * @param type type
     * @param index index
     * @param payload payload
     * @since 0.1.7
     */
    public OutputSchema(String type, int index, Object payload) {
        this.type = type;
        this.index = index;
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
     * getIndex.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getIndex() {
        return index;
    }

    /**
     * setIndex.
     * 
     * @param index index
     * @since 0.1.7
     */
    public void setIndex(int index) {
        this.index = index;
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
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
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

    /**
     * equals.
     * 
     * @param o o
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutputSchema that)) {
            return false;
        }
        return index == that.index && Objects.equals(type, that.type) && Objects.equals(payload, that.payload);
    }

    /**
     * hashCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, index, payload);
    }
}
