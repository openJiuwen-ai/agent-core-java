/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for LLM-based agent in the application layer.
 * <p>
 * Combines agent identity, model configuration, prompt template,
 * workflow/plugin schemas, memory config, and constraint settings.
 * <p>
 * Mirrors Python's {@code LegacyReActAgentConfig} used by LLMAgent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmAgentConfig {

    private String id;

    @Builder.Default
    private String version = "1.0";

    @Builder.Default
    private String description = "";

    @Builder.Default
    private List<WorkflowSchema> workflows = new ArrayList<>();

    @Builder.Default
    private List<PluginSchema> plugins = new ArrayList<>();

    private ModelConfig model;

    @Builder.Default
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    private List<String> tools = new ArrayList<>();

    @Builder.Default
    private String memoryScopeId = "";

    @Builder.Default
    private AgentMemoryConfig agentMemoryConfig = AgentMemoryConfig.builder().build();

    @Builder.Default
    private ConstrainConfig constrain = new ConstrainConfig();

    private ContextEngineConfig contextEngineConfig;

    private DefaultResponse defaultResponse;

    /**
     * Constraint configuration for ReAct loop.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConstrainConfig {
        @Builder.Default
        private int maxIteration = 5;
    }
}
