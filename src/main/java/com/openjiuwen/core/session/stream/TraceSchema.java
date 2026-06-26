/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

/**
 * Mirrors Python's {@code TraceSchema} in
 * {@code openjiuwen/core/session/stream/base.py}.
 */
public class TraceSchema implements StreamSchema {

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
}
