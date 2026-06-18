/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

/**
 * Mirrors Python's {@code OutputSchema} in
 * {@code openjiuwen/core/session/stream/base.py}.
 */
public class OutputSchema implements StreamSchema {

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
}
