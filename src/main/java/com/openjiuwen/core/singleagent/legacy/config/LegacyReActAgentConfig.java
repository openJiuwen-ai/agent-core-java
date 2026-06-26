/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy ReAct agent configuration.
 *
 * <p>Mirrors Python's {@code LegacyReActAgentConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegacyReActAgentConfig extends AgentConfig {
    @JsonProperty("prompt_template_name")
    private String promptTemplateName = "react_system_prompt";

    @JsonProperty("prompt_template")
    private List<Map<String, Object>> promptTemplate = new ArrayList<>();

    private ConstrainConfig constrain = new ConstrainConfig();
    private List<Object> plugins = new ArrayList<>();

    @JsonProperty("memory_scope_id")
    private String memoryScopeId = "";

    @JsonProperty("agent_memory_config")
    private AgentMemoryConfig agentMemoryConfig = new AgentMemoryConfig();

    public LegacyReActAgentConfig() {
        setControllerType(ControllerType.REACT_CONTROLLER);
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public void setPromptTemplateName(String promptTemplateName) {
        this.promptTemplateName = promptTemplateName == null ? "" : promptTemplateName;
    }

    public List<Map<String, Object>> getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(List<Map<String, Object>> promptTemplate) {
        this.promptTemplate = copyPrompt(promptTemplate);
    }

    public ConstrainConfig getConstrain() {
        return constrain;
    }

    public void setConstrain(ConstrainConfig constrain) {
        this.constrain = constrain == null ? new ConstrainConfig() : constrain;
    }

    public List<Object> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<?> plugins) {
        this.plugins = plugins == null ? new ArrayList<>() : new ArrayList<>(plugins);
    }

    public String getMemoryScopeId() {
        return memoryScopeId;
    }

    public void setMemoryScopeId(String memoryScopeId) {
        this.memoryScopeId = memoryScopeId == null ? "" : memoryScopeId;
    }

    public AgentMemoryConfig getAgentMemoryConfig() {
        return agentMemoryConfig;
    }

    public void setAgentMemoryConfig(AgentMemoryConfig agentMemoryConfig) {
        this.agentMemoryConfig = agentMemoryConfig == null ? new AgentMemoryConfig() : agentMemoryConfig;
    }

    public int getContextWindowLimit() {
        return constrain.getReservedMaxChatRounds();
    }

    private static List<Map<String, Object>> copyPrompt(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> item : source) {
                copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
            }
        }
        return copy;
    }
}
