/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code TraceSchema} in
 * {@code openjiuwen/core/session/stream/base.py}.
 */
public class TraceSchema implements StreamSchema {

    private static final long serialVersionUID = 1L;

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

    public static TraceSchema fromMap(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }
        TraceSchema schema = new TraceSchema();
        schema.setType((String) data.get("type"));
        schema.setPayload(data.get("payload"));
        return schema;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TraceSchema that)) {
            return false;
        }
        return Objects.equals(type, that.type) && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload);
    }
}
