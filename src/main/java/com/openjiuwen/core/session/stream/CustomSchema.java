  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom stream schema allowing arbitrary properties.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.base.CustomSchema}.
 */
public class CustomSchema implements WorkflowChunk {

    private final Map<String, Object> properties;

    public CustomSchema() {
        this.properties = new HashMap<>();
    }

    public CustomSchema(Map<String, Object> properties) {
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
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

    /**
     * Validate data from a map.
     *
     * @param data the data map
     * @return a validated CustomSchema instance
     */
    public static CustomSchema fromMap(Map<String, Object> data) {
        return new CustomSchema(data);
    }
}
