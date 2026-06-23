/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.prompts.tools.HarnessPromptToolsPackage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base for harness-local tool implementations.
 *
 * <p>Mirrors Python's {@code ToolOutput}-based harness tool modules in
 * {@code openjiuwen/harness/tools/__init__.py}.</p>
 */
public abstract class AbstractHarnessTool extends Tool {

    protected AbstractHarnessTool(ToolCard card) {
        super(card);
    }

    @Override
    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs)
            throws Exception {
        return List.of(invokeInternal(inputs, kwargs)).iterator();
    }

    protected static ToolCard toolCard(String id, String name, String description) {
        String resolvedDescription = description == null ? "" : description;
        Map<String, Object> inputParams = emptySchema();
        try {
            resolvedDescription = HarnessPromptToolsPackage.getToolDescription(id, "en");
            inputParams = HarnessPromptToolsPackage.getToolInputParams(id, "en");
        } catch (RuntimeException ignored) {
            // Some internal helper tools intentionally have no prompt metadata provider.
        }
        return ToolCard.builder()
                .id(id)
                .name(name)
                .description(resolvedDescription)
                .inputParams(inputParams)
                .build();
    }

    protected static Map<String, Object> emptySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        return schema;
    }

    protected static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    protected static String requiredString(Map<String, Object> inputs, String key) {
        String value = stringValue(inputs == null ? null : inputs.get(key)).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    protected static boolean boolValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "y", "on").contains(text);
    }

    protected static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    protected static Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    protected static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> stringObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        }
        return result;
    }
}
