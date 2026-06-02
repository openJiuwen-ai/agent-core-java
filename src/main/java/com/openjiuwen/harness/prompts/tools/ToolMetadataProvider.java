/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard interface for tool metadata providers.
 * <p>
 * All DeepAgent built-in tools must implement this interface,
 * ensuring complete bilingual descriptions and parameter schemas.
 * <p>
 * Mirrors Python's {@code ToolMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.base}.
 */
public interface ToolMetadataProvider {

    /** Unique name of the tool in the registry. */
    String getName();

    /** Return the tool description in the specified language. */
    String getDescription(String language);

    /** Return the tool description in the default language. */
    default String getDescription() {
        return getDescription("cn");
    }

    /** Return JSON Schema parameter definitions for the specified language. */
    Map<String, Object> getInputParams(String language);

    /** Return JSON Schema parameter definitions in the default language. */
    default Map<String, Object> getInputParams() {
        return getInputParams("cn");
    }

    /** Validate bilingual metadata parity. */
    default void validate() {
        validateProvider(this);
    }

    static void validateProvider(ToolMetadataProvider provider) {
        String name = provider.getName();
        for (String language : new String[] {"cn", "en"}) {
            String description = provider.getDescription(language);
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("[" + name + "] " + language + " description is empty");
            }
        }

        Map<String, Object> cnSchema = provider.getInputParams("cn");
        Map<String, Object> enSchema = provider.getInputParams("en");
        validateSchemaPair(name, cnSchema, enSchema, "cn", "en", "");
    }

    @SuppressWarnings("unchecked")
    private static void validateSchemaPair(
            String name,
            Map<String, Object> refSchema,
            Map<String, Object> otherSchema,
            String refLang,
            String otherLang,
            String path
    ) {
        String prefix = "[" + name + "]" + path;
        if (!"object".equals(refSchema.get("type"))) {
            throw new IllegalArgumentException(prefix + " " + refLang + " schema type != 'object'");
        }
        if (!"object".equals(otherSchema.get("type"))) {
            throw new IllegalArgumentException(prefix + " " + otherLang + " schema type != 'object'");
        }
        if (!refSchema.containsKey("properties")) {
            throw new IllegalArgumentException(prefix + " " + refLang + " schema missing 'properties'");
        }
        if (!refSchema.containsKey("required")) {
            throw new IllegalArgumentException(prefix + " " + refLang + " schema missing 'required'");
        }

        Map<String, Object> refProps = castMap(refSchema.get("properties"));
        Map<String, Object> otherProps = castMap(otherSchema.get("properties"));
        if (!refProps.keySet().equals(otherProps.keySet())) {
            throw new IllegalArgumentException(
                    prefix + " property keys differ: "
                            + refLang + "=" + refProps.keySet() + ", "
                            + otherLang + "=" + otherProps.keySet());
        }

        for (String key : refProps.keySet()) {
            Map<String, Object> refProp = castMap(refProps.get(key));
            Map<String, Object> otherProp = castMap(otherProps.get(key));
            if (!refProp.containsKey("description")) {
                throw new IllegalArgumentException(prefix + "." + key + " " + refLang + " missing description");
            }
            if (!otherProp.containsKey("description")) {
                throw new IllegalArgumentException(prefix + "." + key + " " + otherLang + " missing description");
            }
            if ("object".equals(refProp.get("type")) && refProp.containsKey("properties")) {
                validateSchemaPair(name, refProp, otherProp, refLang, otherLang, path + "." + key);
            }
            if ("array".equals(refProp.get("type")) && refProp.containsKey("items")) {
                Map<String, Object> refItems = castMap(refProp.get("items"));
                Map<String, Object> otherItems = castMap(otherProp.get("items"));
                if ("object".equals(refItems.get("type")) && refItems.containsKey("properties")) {
                    validateSchemaPair(name, refItems, otherItems, refLang, otherLang, path + "." + key + "[]");
                }
            }
        }
    }

    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Collections.emptyMap();
    }
}
