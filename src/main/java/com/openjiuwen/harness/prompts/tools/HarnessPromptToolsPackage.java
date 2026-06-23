/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Registry facade for prompt tool metadata.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/harness/prompts/tools/__init__.py}.</p>
 */
public final class HarnessPromptToolsPackage {

    private static final Map<String, ToolMetadataProvider> REGISTRY = new LinkedHashMap<>();

    static {
        registerBuiltinProvider(new AskUserMetadataProvider());
        registerBuiltinProvider(new BashMetadataProvider());
        registerBuiltinProvider(new PowerShellMetadataProvider());
        registerBuiltinProvider(new CodePromptToolProviders.CodeMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronListJobsMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronGetJobMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronCreateJobMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronUpdateJobMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronDeleteJobMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronToggleJobMetadataProvider());
        registerBuiltinProvider(new CronPromptToolProviders.CronPreviewJobMetadataProvider());
        registerBuiltinProvider(new FilesystemPromptToolProviders.ReadFileMetadataProvider());
        registerBuiltinProvider(new FilesystemPromptToolProviders.WriteFileMetadataProvider());
        registerBuiltinProvider(new FilesystemPromptToolProviders.EditFileMetadataProvider());
        registerBuiltinProvider(new FilesystemPromptToolProviders.GlobMetadataProvider());
        registerBuiltinProvider(new FilesystemPromptToolProviders.ListDirMetadataProvider());
        registerBuiltinProvider(new FilesystemPromptToolProviders.GrepMetadataProvider());
        registerBuiltinProvider(new ListSkillMetadataProvider());
        registerBuiltinProvider(new TodoPromptToolProviders.TodoCreateMetadataProvider());
        registerBuiltinProvider(new TodoPromptToolProviders.TodoListMetadataProvider());
        registerBuiltinProvider(new TodoPromptToolProviders.TodoModifyMetadataProvider());
        registerBuiltinProvider(new ImageOCRMetadataProvider());
        registerBuiltinProvider(new VisualQuestionAnsweringMetadataProvider());
        registerBuiltinProvider(new AudioPromptToolProviders.AudioTranscriptionMetadataProvider());
        registerBuiltinProvider(new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider());
        registerBuiltinProvider(new AudioPromptToolProviders.AudioMetadataMetadataProvider());
        registerBuiltinProvider(new ListMcpResourcesMetadataProvider());
        registerBuiltinProvider(new ReadMcpResourceMetadataProvider());
    }

    private HarnessPromptToolsPackage() {
    }

    public static String getToolDescription(String name, String language) {
        ToolMetadataProvider provider = REGISTRY.get(name);
        if (provider == null) {
            throw toolNotRegistered(name);
        }
        return provider.getDescription(language);
    }

    public static Map<String, Object> getToolInputParams(String name, String language) {
        ToolMetadataProvider provider = REGISTRY.get(name);
        if (provider == null) {
            throw toolNotRegistered(name);
        }
        return provider.getInputParams(language);
    }

    public static ToolCard buildToolCard(String name, String toolId, String language) {
        return buildToolCard(name, toolId, language, null, null);
    }

    public static ToolCard buildToolCard(String name, String toolId, String language, String agentId) {
        return buildToolCard(name, toolId, language, null, agentId);
    }

    public static ToolCard buildToolCard(
            String name,
            String toolId,
            String language,
            Map<String, String> formatArgs,
            String agentId) {
        String description = getToolDescription(name, language);
        if (formatArgs != null) {
            for (Map.Entry<String, String> entry : formatArgs.entrySet()) {
                description = description.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        String prefix = toolId == null || toolId.isBlank() ? name : toolId;
        String finalToolId = prefix + "_" + (agentId != null ? agentId : UUID.randomUUID().toString().replace("-", ""));
        return new ToolCard(
                finalToolId,
                name,
                description,
                getToolInputParams(name, language)
        );
    }

    public static void registerToolProvider(ToolMetadataProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        provider.validate();
        REGISTRY.put(provider.getName(), provider);
    }

    public static void validateAllToolProviders() {
        REGISTRY.values().forEach(ToolMetadataProvider::validate);
    }

    public static PromptSection buildToolsSection(Map<String, String> toolDescriptions, String language) {
        if (toolDescriptions == null || toolDescriptions.isEmpty()) {
            return null;
        }
        String resolvedLanguage = language == null ? "cn" : language;
        String header = "en".equals(resolvedLanguage) ? "## Available Tools" : "## 可用工具";
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(header);
        for (Map.Entry<String, String> entry : toolDescriptions.entrySet()) {
            joiner.add("- **" + entry.getKey() + "**: " + entry.getValue());
        }
        return new PromptSection(SectionName.TOOLS, Map.of(resolvedLanguage, joiner.toString()), 40);
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

    private static void registerBuiltinProvider(ToolMetadataProvider provider) {
        if (provider != null && provider.getName() != null) {
            REGISTRY.put(provider.getName(), provider);
        }
    }

    private static NoSuchElementException toolNotRegistered(String name) {
        return new NoSuchElementException("Tool '" + name + "' not registered. Available: " + REGISTRY.keySet());
    }
}
