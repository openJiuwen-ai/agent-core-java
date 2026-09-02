/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input model for the Tool workflow component.
 * <p>
 * Mirrors Python's {@code ToolComponentInput} Pydantic model with {@code extra='allow'}.
 * Accepts arbitrary key/value entries as tool inputs.
 * 
 * @since 0.1.7
 */
public class ToolComponentInput {
    private final Map<String, Object> fields;

    /**
     * ToolComponentInput.
     * 
     * @since 0.1.7
     */
    public ToolComponentInput() {
        this.fields = new LinkedHashMap<>();
    }

    /**
     * ToolComponentInput.
     * 
     * @param fields fields
     * @since 0.1.7
     */
    public ToolComponentInput(Map<String, Object> fields) {
        this.fields = fields != null ? new LinkedHashMap<>(fields) : new LinkedHashMap<>();
    }

    /**
     * getFields.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getFields() {
        return fields;
    }

    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object get(String key) {
        return fields.get(key);
    }

    /**
     * put.
     * 
     * @param key key
     * @param value value
     * @since 0.1.7
     */
    public void put(String key, Object value) {
        fields.put(key, value);
    }

    /**
     * Convert to a plain map (for tool invocation).
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(fields);
    }

    /**
     * fromMap.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static ToolComponentInput fromMap(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            return new ToolComponentInput((Map<String, Object>) map);
        }
        return new ToolComponentInput();
    }
}
