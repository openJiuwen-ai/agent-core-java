/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for harness built-in tool metadata providers.
 *
 * <p>Aligned with Python openjiuwen's harness.prompts.sections.tools package API.
 *
 * @since 0.1.7
 */
public final class ToolMetadataRegistry {
    private static final Map<String, ToolMetadataProvider> REGISTRY =
            new ConcurrentHashMap<String, ToolMetadataProvider>();

    static {
        registerToolProvider(new AskUserMetadataProvider());
    }

    private ToolMetadataRegistry() {
    }

    /**
     * Register a provider in the harness metadata registry.
     *
     * @param provider metadata provider to register
     */
    public static void registerToolProvider(ToolMetadataProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider is null");
        }
        provider.validate();
        REGISTRY.put(provider.getName(), provider);
    }

    /**
     * Look up a localized tool description.
     *
     * @param name tool name
     * @param language target language code
     * @return localized description
     */
    public static String getToolDescription(String name, String language) {
        return provider(name).getDescription(language);
    }

    /**
     * Look up a localized tool input schema.
     *
     * @param name tool name
     * @param language target language code
     * @return JSON-schema-like input definition
     */
    public static Map<String, Object> getToolInputParams(String name, String language) {
        return provider(name).getInputParams(language);
    }

    /**
     * Build a tool card from registered metadata.
     *
     * @param name tool name
     * @param toolId concrete tool id
     * @param language target language code
     * @return resolved tool card
     */
    public static ToolCard buildToolCard(String name, String toolId, String language) {
        return ToolCard.builder()
                .id(toolId)
                .name(name)
                .description(getToolDescription(name, language))
                .inputParams(getToolInputParams(name, language))
                .build();
    }

    private static ToolMetadataProvider provider(String name) {
        ToolMetadataProvider provider = REGISTRY.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Tool '" + name + "' not registered");
        }
        return provider;
    }
}
