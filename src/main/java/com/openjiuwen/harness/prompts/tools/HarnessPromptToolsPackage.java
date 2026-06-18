/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Registry facade for prompt tool metadata.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/harness/prompts/tools/__init__.py}.</p>
 */
public final class HarnessPromptToolsPackage {

    private static final Map<String, ToolMetadataProvider> REGISTRY = new LinkedHashMap<>();

    static {
        registerToolProvider(new AskUserMetadataProvider());
        registerToolProvider(new BashMetadataProvider());
        registerToolProvider(new PowerShellMetadataProvider());
        registerToolProvider(new CodePromptToolProviders.CodeMetadataProvider());
        registerToolProvider(new FilesystemPromptToolProviders.ReadFileMetadataProvider());
        registerToolProvider(new ListSkillMetadataProvider());
    }

    private HarnessPromptToolsPackage() {
    }

    public static String getToolDescription(String name, String language) {
        ToolMetadataProvider provider = REGISTRY.get(name);
        return provider == null ? "" : provider.getDescription(language);
    }

    public static Map<String, Object> getToolInputParams(String name, String language) {
        ToolMetadataProvider provider = REGISTRY.get(name);
        return provider == null ? Map.of("type", "object", "properties", Map.of(), "required", java.util.List.of())
                : provider.getInputParams(language);
    }

    public static ToolCard buildToolCard(String name, String toolId, String language) {
        return new ToolCard(
                toolId == null ? name : toolId,
                name,
                getToolDescription(name, language),
                getToolInputParams(name, language)
        );
    }

    public static void registerToolProvider(ToolMetadataProvider provider) {
        if (provider != null && provider.getName() != null) {
            REGISTRY.put(provider.getName(), provider);
        }
    }

    public static void validateAllToolProviders() {
        REGISTRY.values().forEach(ToolMetadataProvider::validate);
    }

    public static String buildToolsSection(Iterable<String> toolDescriptions, String language) {
        StringJoiner joiner = new StringJoiner("\n\n");
        if (toolDescriptions != null) {
            for (String description : toolDescriptions) {
                if (description != null && !description.isBlank()) {
                    joiner.add(description);
                }
            }
        }
        String header = "en".equals(language) ? "# Tools" : "# 工具";
        return header + "\n\n" + joiner;
    }
}
