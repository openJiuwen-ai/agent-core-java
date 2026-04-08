/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates JSON schema from output configuration.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.SchemaGenerator}.
 */
public final class SchemaGenerator {

    private SchemaGenerator() {
    }

    /**
     * Generate a JSON schema from output configuration.
     *
     * @param outputsConfig field name to field config mapping
     * @return generated JSON schema
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> generateJsonSchema(Map<String, Object> outputsConfig) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Map.Entry<String, Object> entry : outputsConfig.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldConfig;
            if (entry.getValue() instanceof Map) {
                fieldConfig = (Map<String, Object>) entry.getValue();
            } else {
                continue;
            }

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", fieldConfig.getOrDefault("type", "string"));
            prop.put("description", fieldConfig.getOrDefault("description", ""));

            if ("array".equals(fieldConfig.get("type")) && fieldConfig.containsKey("items")) {
                prop.put("items", fieldConfig.get("items"));
            }
            if ("object".equals(fieldConfig.get("type")) && fieldConfig.containsKey("properties")) {
                prop.put("properties", fieldConfig.get("properties"));
            }

            properties.put(fieldName, prop);

            Object reqVal = fieldConfig.getOrDefault("required", true);
            if (Boolean.TRUE.equals(reqVal)) {
                required.add(fieldName);
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }
}
