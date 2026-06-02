/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Central tool description registry and card builder.
 * <p>
 * All built-in tools register via {@link ToolMetadataProvider} implementations.
 * <p>
 * Mirrors Python's {@code build_tool_card / build_tools_section / register_tool_provider}
 * in {@code openjiuwen.harness.prompts.tools.__init__}.
 */
public final class ToolDescriptionRegistry {

    private static final Map<String, ToolMetadataProvider> REGISTRY = new LinkedHashMap<>();

    private ToolDescriptionRegistry() {
    }

    private static void ensureRegistered() {
        if (REGISTRY.isEmpty()) {
            BuiltinToolProviders.registerAll();
        }
    }

    /** Register a tool provider. */
    public static void register(ToolMetadataProvider provider) {
        provider.validate();
        REGISTRY.put(provider.getName(), provider);
    }

    /** Look up a tool description. Throws if not found. */
    public static String getToolDescription(String name, String language) {
        ensureRegistered();
        ToolMetadataProvider provider = REGISTRY.get(name);
        if (provider == null) {
            throw new KeyError("Tool '" + name + "' not registered. Available: "
                    + new TreeSet<>(REGISTRY.keySet()));
        }
        return provider.getDescription(language);
    }

    /** Look up tool input params schema. Throws if not found. */
    public static Map<String, Object> getToolInputParams(String name, String language) {
        ensureRegistered();
        ToolMetadataProvider provider = REGISTRY.get(name);
        if (provider == null) {
            throw new KeyError("Tool '" + name + "' not registered. Available: "
                    + new TreeSet<>(REGISTRY.keySet()));
        }
        return provider.getInputParams(language);
    }

    /** Build a ToolCard-like map for the given tool. */
    public static Map<String, Object> buildToolCard(String name, String toolIdPrefix,
                                                     String language, String agentId) {
        ensureRegistered();
        String description = getToolDescription(name, language);
        String finalToolId = agentId != null
                ? toolIdPrefix + "_" + agentId
                : toolIdPrefix + "_" + UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", finalToolId);
        card.put("name", name);
        card.put("description", description);
        card.put("input_params", getToolInputParams(name, language));
        return card;
    }

    /**
     * Build a dynamic tools section from tool descriptions.
     * Returns null if no tool descriptions are provided.
     */
    public static PromptSection buildToolsSection(Map<String, String> toolDescriptions,
                                                   String language) {
        if (toolDescriptions == null || toolDescriptions.isEmpty()) {
            return null;
        }
        String lang = "en".equalsIgnoreCase(language) ? "en" : "cn";

        StringBuilder sb = new StringBuilder();
        sb.append("en".equals(lang) ? "## Available Tools" : "## 可用工具");
        sb.append("\n");
        for (Map.Entry<String, String> entry : toolDescriptions.entrySet()) {
            sb.append("- **").append(entry.getKey()).append("**: ")
                    .append(entry.getValue()).append("\n");
        }

        Map<String, String> content = Collections.singletonMap(lang, sb.toString());
        return new PromptSection(SectionName.TOOLS, content, 40);
    }

    /** Get all registered provider names. */
    public static Set<String> getRegisteredNames() {
        ensureRegistered();
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /** Python-style runtime registration alias. */
    public static void registerToolProvider(ToolMetadataProvider provider) {
        register(provider);
    }

    /** Validate all registered providers. */
    public static void validateAllToolProviders() {
        ensureRegistered();
        for (ToolMetadataProvider provider : REGISTRY.values()) {
            provider.validate();
        }
    }

    /** Simple KeyError for fail-fast behavior. */
    public static class KeyError extends RuntimeException {
        public KeyError(String message) {
            super(message);
        }
    }
}
