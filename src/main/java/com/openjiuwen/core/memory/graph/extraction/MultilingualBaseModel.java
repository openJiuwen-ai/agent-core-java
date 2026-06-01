/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Java equivalent of Python's {@code readable_schema} tuple. */
    public record ReadableSchema(String outputSchema, Map<String, Object> refs) {
    }

    /**
     * Get the response format map for structured output.
     *
     * @return a map describing the response format
     */
    public abstract Map<String, Object> responseFormat();

    /**
     * Convert this model to the LLM response-format wrapper used by Python.
     *
     * @param language the language code (cn/en)
     * @return a response format map with a strict JSON schema payload
     */
    public Map<String, Object> responseFormat(String language) {
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("schema", multilingualModelJsonSchema(language, true));
        jsonSchema.put("name", getClass().getSimpleName());
        jsonSchema.put("strict", false);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "json_schema");
        wrapper.put("json_schema", jsonSchema);
        return wrapper;
    }

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
     * Generate a compact, LLM-readable schema description and referenced definitions.
     *
     * @param language the language code (cn/en)
     * @return formatted schema text plus referenced object definitions
     */
    @SuppressWarnings("unchecked")
    public ReadableSchema readableSchema(String language) {
        Map<String, Object> schema = multilingualModelJsonSchema(language, false);
        Map<String, Object> refs = new LinkedHashMap<>();
        Object defs = schema.get("$defs");
        if (defs instanceof Map<?, ?> defsMap) {
            for (Map.Entry<?, ?> entry : defsMap.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof Map<?, ?> def) {
                    Object properties = def.get("properties");
                    if (properties instanceof Map<?, ?> propertiesMap) {
                        refs.put(key, new LinkedHashMap<>((Map<String, Object>) propertiesMap));
                    }
                }
            }
        }

        StringBuilder output = new StringBuilder();
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertiesMap) {
            for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> prop)) {
                    continue;
                }
                if (!output.isEmpty()) {
                    output.append('\n');
                }
                output.append(key).append(": ").append(schemaType((Map<String, Object>) prop));
                Object description = prop.get("description");
                if (description instanceof String desc && !desc.isEmpty()) {
                    output.append("  # ").append(desc);
                }
            }
        }
        return new ReadableSchema(output.toString(), refs);
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
        if (schema.containsKey(fromKey) && schema.get(fromKey) instanceof String value) {
            schema.remove(fromKey);
            if (toKey != null) {
                schema.put(toKey, lookup.getOrDefault(value, value));
            }
        }
        for (Object value : new ArrayList<>(schema.values())) {
            if (value instanceof Map<?, ?> child) {
                recursiveReplace((Map<String, Object>) child, lookup, fromKey, toKey);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> child) {
                        recursiveReplace((Map<String, Object>) child, lookup, fromKey, toKey);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enforceStrictMode(Map<String, Object> schema) {
        List<Object> toVisit = new ArrayList<>();
        toVisit.add(schema);
        for (int i = 0; i < toVisit.size(); i++) {
            Object current = toVisit.get(i);
            if (current instanceof Map<?, ?> node) {
                Map<String, Object> currentMap = (Map<String, Object>) node;
                if ("object".equals(currentMap.get("type")) && currentMap.get("properties") instanceof Map<?, ?> props) {
                    currentMap.put("additionalProperties", false);
                    currentMap.putIfAbsent("required", new ArrayList<>(((Map<String, Object>) props).keySet()));
                }
                toVisit.addAll(currentMap.values());
            } else if (current instanceof List<?> list) {
                toVisit.addAll(list);
            }
        }
    }

    /**
     * Convert Java reflection types into the compact type strings used in tests.
     */
    public static String toJsonTypes(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.getSimpleName();
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type raw = parameterizedType.getRawType();
            String rawName = raw instanceof Class<?> clazz ? clazz.getSimpleName() : raw.getTypeName();
            Type[] args = parameterizedType.getActualTypeArguments();
            if (args.length == 0) {
                return rawName;
            }
            List<String> argNames = new ArrayList<>();
            for (Type arg : args) {
                argNames.add(toJsonTypes(arg));
            }
            return rawName + "[" + String.join(",", argNames) + "]";
        }
        return type.getTypeName();
    }

    private static String schemaType(Map<String, Object> property) {
        Object type = property.get("type");
        if ("array".equals(type) && property.get("items") instanceof Map<?, ?> items) {
            Object ref = items.get("$ref");
            if (ref instanceof String refString) {
                int idx = refString.lastIndexOf('/');
                String refName = idx >= 0 ? refString.substring(idx + 1) : refString;
                return "array[" + refName + "]";
            }
            Object itemType = items.get("type");
            return "array[" + (itemType == null ? "object" : itemType) + "]";
        }
        return type == null ? "object" : type.toString();
    }
}
