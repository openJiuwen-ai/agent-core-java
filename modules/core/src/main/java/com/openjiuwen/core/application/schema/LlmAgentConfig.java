/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LlmAgentConfig {
    private String id;

    @Builder.Default
    private String version = "1.0";

    @Builder.Default
    private String description = "";

    @Builder.Default
    @JsonProperty("controller_type")
    @JsonAlias("controllerType")
    private ControllerType controllerType = ControllerType.REACT_CONTROLLER;

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<WorkflowSchema> workflows = new ArrayList<>();

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<PluginSchema> plugins = new ArrayList<>();

    private ModelConfig model;

    @Builder.Default
    @JsonProperty("prompt_template_name")
    @JsonAlias("promptTemplateName")
    private String promptTemplateName = "react_system_prompt";

    @Builder.Default
    @JsonProperty("prompt_template")
    @JsonAlias("promptTemplate")
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> tools = new ArrayList<>();

    @Builder.Default
    @JsonProperty("memory_scope_id")
    @JsonAlias("memoryScopeId")
    private String memoryScopeId = "";

    @Builder.Default
    @JsonProperty("agent_memory_config")
    @JsonAlias("agentMemoryConfig")
    /**
     * AgentMemoryConfig.builder.
     * 
     * @since 0.1.7
     */
    private AgentMemoryConfig agentMemoryConfig = AgentMemoryConfig.builder().build();

    @Builder.Default
    /**
     * ConstrainConfig.builder.
     * 
     * @since 0.1.7
     */
    private ConstrainConfig constrain = ConstrainConfig.builder().build();

    @JsonProperty("context_engine_config")
    @JsonAlias("contextEngineConfig")
    private ContextEngineConfig contextEngineConfig;

    @JsonProperty("default_response")
    @JsonAlias("defaultResponse")
    private DefaultResponse defaultResponse;

    /**
     * getContextWindowLimit.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonProperty("context_window_limit")
    public int getContextWindowLimit() {
        return constrain != null ? constrain.getReservedMaxChatRounds() : 10;
    }
}
