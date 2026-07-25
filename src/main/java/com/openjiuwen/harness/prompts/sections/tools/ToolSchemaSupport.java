/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for building JSON-schema-like tool input definitions.
 *
 * @since 0.1.12
 */
final class ToolSchemaSupport {
    private ToolSchemaSupport() {
    }

    static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        );
    }

    static Map<String, Object> properties(Object[] entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("property entries must be key/value pairs");
        }
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) {
            Object key = entries[i];
            if (!(key instanceof String keyText)) {
                throw new IllegalArgumentException("property key must be string");
            }
            properties.put(keyText, entries[i + 1]);
        }
        return properties;
    }

    static Map<String, Object> property(String type, String description) {
        return Map.of(
                "type", type,
                "description", description
        );
    }

    static Map<String, Object> enumProperty(String type, List<String> values, String description) {
        return Map.of(
                "type", type,
                "enum", values,
                "description", description
        );
    }

    static String localized(String language, String cn, String en) {
        return "en".equals(language) ? en : cn;
    }
}
