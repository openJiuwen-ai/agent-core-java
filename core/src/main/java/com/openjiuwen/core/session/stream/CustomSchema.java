/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code CustomSchema} in
 * {@code openjiuwen/core/session/stream/base.py}.
 */
public class CustomSchema implements StreamSchema {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> properties = new LinkedHashMap<>();

    public CustomSchema() {
    }

    public CustomSchema(Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public Object get(String key) {
        return properties.get(key);
    }

    public void put(String key, Object value) {
        properties.put(key, value);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public static CustomSchema fromMap(Map<String, Object> data) {
        return new CustomSchema(data);
    }
}
