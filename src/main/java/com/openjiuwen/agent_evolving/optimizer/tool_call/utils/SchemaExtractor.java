/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extract schema structure from JSON schema.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.schema_extractor}.
 */
public final class SchemaExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SchemaExtractor() {
        // Utility class
    }

    /**
     * Extract schema structure without type information.
     *
     * @param schemaDict Schema dictionary or JSON string
     * @return Extracted schema structure
     */
    public static Map<String, Object> extractSchema(Object schemaDict) {
        Map<String, Object> schemaMap = null;

        if (schemaDict instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) schemaDict;
            schemaMap = map;
        } else if (schemaDict instanceof String) {
            try {
                schemaMap = OBJECT_MAPPER.readValue(
                        (String) schemaDict,
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        } else {
            return new LinkedHashMap<>();
        }

        return extractSchemaRecursive(schemaMap);
    }

    private static Map<String, Object> extractSchemaRecursive(Map<String, Object> schemaDict) {
        if (schemaDict == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemaDict.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                result.put(key, extractSchemaRecursive(nestedMap));
            } else if (value instanceof List) {
                result.put(key, value);
            } else {
                result.put(key, "");
            }
        }
        return result;
    }
}
