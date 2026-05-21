/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.util.HashMap;
import java.util.Map;

/**
 * Multilingual base model for LLM response schema generation.
 * <p>
 * Mirrors Python's {@code MultilingualBaseModel} class from
 * <code>memory/graph/extraction/base.py</code>.
 *
 * <p>Provides multilingual description lookup and JSON schema generation
 * with OpenAI structured output compliance.
 */
public abstract class MultilingualBaseModel {

    /** Multilingual description registry, populated by prompt files. */
    protected static final Map<String, Map<String, String>> MULTILINGUAL_DESCRIPTION = new HashMap<>();

    /**
     * Get the response format map for structured output.
     *
     * @return a map describing the response format
     */
    public abstract Map<String, Object> responseFormat();

    /**
     * Get JSON schema with multilingual descriptions replaced.
     *
     * @param language the language code (cn/en)
     * @param strict   whether to enforce strict mode (additionalProperties: false)
     * @return the JSON schema as a map
     */
    public Map<String, Object> multilingualModelJsonSchema(String language, boolean strict) {
        Map<String, Object> schema = responseFormat();
        Map<String, String> descLookup = MULTILINGUAL_DESCRIPTION.getOrDefault(language, new HashMap<>());
        recursiveReplace(schema, descLookup, "description", "description");
        if (strict) {
            enforceStrictMode(schema);
        }
        return schema;
    }

    /**
     * Recursively replace values in a nested map structure.
     */
    @SuppressWarnings("unchecked")
    protected static void recursiveReplace(Map<String, Object> schema, Map<String, String> lookup, String fromKey) {
        recursiveReplace(schema, lookup, fromKey, fromKey);
    }

    @SuppressWarnings("unchecked")
    protected static void recursiveReplace(Map<String, Object> schema, Map<String, String> lookup, String fromKey, String toKey) {
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if (entry.getKey().equals(fromKey) && entry.getValue() instanceof String) {
                String value = (String) entry.getValue();
                if (lookup.containsKey(value)) {
                    schema.put(toKey, lookup.get(value));
                }
            }
            if (entry.getValue() instanceof Map) {
                recursiveReplace((Map<String, Object>) entry.getValue(), lookup, fromKey, toKey);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enforceStrictMode(Map<String, Object> schema) {
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if ("object".equals(entry.getValue()) || ("type".equals(entry.getKey()) && "object".equals(entry.getValue()))) {
                // handled below
            }
            if (entry.getValue() instanceof Map) {
                Map<String, Object> node = (Map<String, Object>) entry.getValue();
                if ("object".equals(node.get("type"))) {
                    Map<String, Object> properties = (Map<String, Object>) node.get("properties");
                    if (properties != null) {
                        node.put("additionalProperties", false);
                        node.putIfAbsent("required", new java.util.ArrayList<>(properties.keySet()));
                    }
                }
                enforceStrictMode(node);
            }
        }
    }
}
