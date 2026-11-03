/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extract schema structure without type information.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/schema_extractor.py}.</p>
 */
public final class SchemaExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SchemaExtractor() {
        // Utility class
    }

    /**
     * Extract the schema shape from a map or a JSON string.
     *
     * @param schemaDict source schema map or JSON string
     * @return schema shape with scalar leaves replaced by empty strings
     */
    public static Map<String, Object> extractSchema(Object schemaDict) {
        Map<String, Object> schemaMap;
        if (schemaDict instanceof Map<?, ?> inputMap) {
            schemaMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                schemaMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else if (schemaDict instanceof String inputText) {
            try {
                schemaMap = OBJECT_MAPPER.readValue(inputText, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (Exception exception) {
                return new LinkedHashMap<>();
            }
        } else {
            return new LinkedHashMap<>();
        }
        return extractSchemaRecursive(schemaMap);
    }

    private static Map<String, Object> extractSchemaRecursive(Map<String, Object> schemaDict) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemaDict.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                Map<String, Object> nested = new LinkedHashMap<>();
                for (Map.Entry<?, ?> nestedEntry : nestedMap.entrySet()) {
                    nested.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
                }
                result.put(entry.getKey(), extractSchemaRecursive(nested));
            } else if (value instanceof List<?>) {
                result.put(entry.getKey(), value);
            } else {
                result.put(entry.getKey(), "");
            }
        }
        return result;
    }
}
