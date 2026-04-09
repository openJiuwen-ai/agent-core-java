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
 */
public class ToolComponentInput {

    private final Map<String, Object> fields;

    public ToolComponentInput() {
        this.fields = new LinkedHashMap<>();
    }

    public ToolComponentInput(Map<String, Object> fields) {
        this.fields = fields != null ? new LinkedHashMap<>(fields) : new LinkedHashMap<>();
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public Object get(String key) {
        return fields.get(key);
    }

    public void put(String key, Object value) {
        fields.put(key, value);
    }

    /**
     * Convert to a plain map (for tool invocation).
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(fields);
    }

    /**
     * Create from a map.
     * Mirrors Python's {@code ToolComponentInput(**inputs).model_dump()}.
     */
    @SuppressWarnings("unchecked")
    public static ToolComponentInput fromMap(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            return new ToolComponentInput((Map<String, Object>) map);
        }
        return new ToolComponentInput();
    }
}
