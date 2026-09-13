/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for harness built-in tool metadata providers.
 * <p>
 * Aligned with Python openjiuwen's harness.prompts.sections.tools package API.
 * 
 * @since 0.1.7
 */
public final class ToolMetadataRegistry {
    private static final Map<String, ToolMetadataProvider> REGISTRY =
        new ConcurrentHashMap<String, ToolMetadataProvider>();

    static {
        registerToolProvider(new AskUserMetadataProvider());
        registerToolProvider(new BashMetadataProvider());
        registerToolProvider(new PowerShellMetadataProvider());
        registerToolProvider(new CodeMetadataProvider());
        registerToolProvider(new CronMetadataProvider());
        registerToolProvider(new ReadFileMetadataProvider());
        registerToolProvider(new WriteFileMetadataProvider());
        registerToolProvider(new EditFileMetadataProvider());
        registerToolProvider(new GlobMetadataProvider());
        registerToolProvider(new ListDirMetadataProvider());
        registerToolProvider(new GrepMetadataProvider());
        registerToolProvider(new DiscoveryMetadataProviders.ListSkillMetadataProvider());
        registerToolProvider(new DiscoveryMetadataProviders.SearchToolsMetadataProvider());
        registerToolProvider(new DiscoveryMetadataProviders.LoadToolsMetadataProvider());
        registerToolProvider(new DiscoveryMetadataProviders.SkillToolMetadataProvider());
        registerToolProvider(new AudioMetadataProviders.AudioTranscriptionMetadataProvider());
        registerToolProvider(new AudioMetadataProviders.AudioQuestionAnsweringMetadataProvider());
        registerToolProvider(new AudioMetadataProviders.AudioMetadataMetadataProvider());
        registerToolProvider(new VisionMetadataProviders.ImageOCRMetadataProvider());
        registerToolProvider(new VisionMetadataProviders.VisualQuestionAnsweringMetadataProvider());
        registerToolProvider(new VisionMetadataProviders.VideoUnderstandingMetadataProvider());
        registerToolProvider(new LspToolMetadataProvider());
        registerToolProvider(new WebMetadataProviders.FreeSearchMetadataProvider());
        registerToolProvider(new WebMetadataProviders.PaidSearchMetadataProvider());
        registerToolProvider(new WebMetadataProviders.FetchWebpageMetadataProvider());
        registerToolProvider(new McpMetadataProviders.ListMcpResourcesMetadataProvider());
        registerToolProvider(new McpMetadataProviders.ReadMcpResourceMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.MemorySearchMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.MemoryGetMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.WriteMemoryMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.EditMemoryMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.ReadMemoryMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.CodingMemoryReadMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.CodingMemoryWriteMetadataProvider());
        registerToolProvider(new MemoryMetadataProviders.CodingMemoryEditMetadataProvider());
        registerToolProvider(new SwitchModeMetadataProvider());
        registerToolProvider(new EnterPlanModeMetadataProvider());
        registerToolProvider(new ExitPlanModeMetadataProvider());
        registerToolProvider(new TaskMetadataProvider());
        registerToolProvider(new SessionsListMetadataProvider());
        registerToolProvider(new SessionsSpawnMetadataProvider());
        registerToolProvider(new SessionsCancelMetadataProvider());
        registerToolProvider(new TodoCreateMetadataProvider());
        registerToolProvider(new TodoListMetadataProvider());
        registerToolProvider(new TodoModifyMetadataProvider());
        registerToolProvider(new TodoGetMetadataProvider());
    }

    /**
     * ToolMetadataRegistry.
     * 
     * @since 0.1.7
     */
    private ToolMetadataRegistry() {
    }

    /**
     * Register a provider in the harness metadata registry.
     * 
     * @param provider metadata provider to register
     * @since 0.1.7
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
     * @since 0.1.7
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
     * @since 0.1.7
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
     * @return isResolved tool card
     * @since 0.1.7
     */
    public static ToolCard buildToolCard(String name, String toolId, String language) {
        return ToolCard.builder().id(toolId).name(name).description(getToolDescription(name, language))
                .inputParams(getToolInputParams(name, language)).build();
    }

    /**
     * provider.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static ToolMetadataProvider provider(String name) {
        ToolMetadataProvider provider = REGISTRY.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Tool '" + name + "' not registered");
        }
        return provider;
    }
}
