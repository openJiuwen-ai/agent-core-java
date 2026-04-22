/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.Map;

/**
 * Harness built-in tool metadata provider.
 *
 * <p>Aligned with Python openjiuwen's harness.prompts.sections.tools.base.ToolMetadataProvider.
 *
 * @since 0.1.7
 */
public interface ToolMetadataProvider {
    /**
     * Return the unique tool name used by the registry.
     *
     * @return tool name
     */
    String getName();

    /**
     * Return the localized description of this tool.
     *
     * @param language target language code
     * @return localized description
     */
    String getDescription(String language);

    /**
     * Return the localized input schema of this tool.
     *
     * @param language target language code
     * @return JSON-schema-like input definition
     */
    Map<String, Object> getInputParams(String language);

    /**
     * Validate the metadata completeness of this provider.
     */
    default void validate() {
        validateLanguage(this, "cn");
        validateLanguage(this, "en");
    }

    /**
     * Validate the localized metadata of this provider.
     *
     * @param provider metadata provider
     * @param language language code
     */
    private static void validateLanguage(ToolMetadataProvider provider, String language) {
        String description = provider.getDescription(language);
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "[" + provider.getName() + "] " + language + " description is empty");
        }

        Map<String, Object> schema = provider.getInputParams(language);
        if (schema == null) {
            throw new IllegalArgumentException("[" + provider.getName() + "] " + language + " schema is null");
        }
        Object type = schema.get("type");
        if (!"object".equals(type)) {
            throw new IllegalArgumentException(
                    "[" + provider.getName() + "] " + language + " schema type must be object");
        }
        if (!schema.containsKey("properties")) {
            throw new IllegalArgumentException(
                    "[" + provider.getName() + "] " + language + " schema missing properties");
        }
        if (!schema.containsKey("required")) {
            throw new IllegalArgumentException(
                    "[" + provider.getName() + "] " + language + " schema missing required");
        }
    }
}
