/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ToolMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/base.py}.
 */
public interface ToolMetadataProvider {

    String getName();

    default String getDescription() {
        return getDescription("cn");
    }

    String getDescription(String language);

    default Map<String, Object> getInputParams() {
        return getInputParams("cn");
    }

    Map<String, Object> getInputParams(String language);

    default void validate() {
        validateProvider(this);
    }

    static void validateProvider(ToolMetadataProvider provider) {
        String name = provider.getName();
        for (String language : com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder.SUPPORTED_LANGUAGES) {
            String description = provider.getDescription(language);
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("[" + name + "] " + language + " description is empty");
            }
        }

        Map<String, Object> referenceSchema = provider.getInputParams("cn");
        Map<String, Object> otherSchema = provider.getInputParams("en");
        validateSchemaPair(name, referenceSchema, otherSchema, "cn", "en", "");
    }

    @SuppressWarnings("unchecked")
    private static void validateSchemaPair(
            String name,
            Map<String, Object> referenceSchema,
            Map<String, Object> otherSchema,
            String referenceLanguage,
            String otherLanguage,
            String path) {
        String prefix = "[" + name + "]" + path;

        if (!"object".equals(referenceSchema.get("type"))) {
            throw new IllegalArgumentException(prefix + " " + referenceLanguage + " schema type != 'object'");
        }
        if (!"object".equals(otherSchema.get("type"))) {
            throw new IllegalArgumentException(prefix + " " + otherLanguage + " schema type != 'object'");
        }
        if (!referenceSchema.containsKey("properties")) {
            throw new IllegalArgumentException(prefix + " " + referenceLanguage + " schema missing 'properties'");
        }
        if (!referenceSchema.containsKey("required")) {
            throw new IllegalArgumentException(prefix + " " + referenceLanguage + " schema missing 'required'");
        }

        Map<String, Object> referenceProps = castMap(referenceSchema.get("properties"));
        Map<String, Object> otherProps = castMap(otherSchema.get("properties"));
        if (!referenceProps.keySet().equals(otherProps.keySet())) {
            throw new IllegalArgumentException(
                    prefix + " property keys differ: "
                            + referenceLanguage + "=" + referenceProps.keySet() + ", "
                            + otherLanguage + "=" + otherProps.keySet()
            );
        }

        for (String key : referenceProps.keySet()) {
            Map<String, Object> referenceProp = castMap(referenceProps.get(key));
            Map<String, Object> otherProp = castMap(otherProps.get(key));
            if (!referenceProp.containsKey("description")) {
                throw new IllegalArgumentException(prefix + "." + key + " " + referenceLanguage + " missing description");
            }
            if (!otherProp.containsKey("description")) {
                throw new IllegalArgumentException(prefix + "." + key + " " + otherLanguage + " missing description");
            }
            if ("object".equals(referenceProp.get("type")) && referenceProp.containsKey("properties")) {
                validateSchemaPair(name, referenceProp, otherProp, referenceLanguage, otherLanguage, path + "." + key);
            }
            if ("array".equals(referenceProp.get("type")) && referenceProp.containsKey("items")) {
                Map<String, Object> referenceItems = castMap(referenceProp.get("items"));
                Map<String, Object> otherItems = castMap(otherProp.get("items"));
                if ("object".equals(referenceItems.get("type")) && referenceItems.containsKey("properties")) {
                    validateSchemaPair(name, referenceItems, otherItems, referenceLanguage, otherLanguage, path + "." + key + "[]");
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
