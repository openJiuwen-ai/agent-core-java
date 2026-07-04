/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.application.llm_agent.LLMAgent;
import com.openjiuwen.core.application.llm_agent.LLMAgentFactory;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.Map;

/**
 * Backward-compatible facade for the 0.1.12 LLM agent class.
 *
 * <p>Mirrors Python's {@code LLMAgent} in
 * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.</p>
 */
public class LlmAgent extends LLMAgent {

    public LlmAgent(LegacyReActAgentConfig agentConfig) {
        super(agentConfig);
    }

    public static LegacyReActAgentConfig createLlmAgentConfig(String agentId,
                                                              String agentVersion,
                                                              String description,
                                                              List<?> workflows,
                                                              List<?> plugins,
                                                              ModelConfig model,
                                                              List<Map<String, Object>> promptTemplate) {
        return createLlmAgentConfig(
                agentId,
                agentVersion,
                description,
                workflows,
                plugins,
                model,
                promptTemplate,
                null
        );
    }

    public static LegacyReActAgentConfig createLlmAgentConfig(String agentId,
                                                              String agentVersion,
                                                              String description,
                                                              List<?> workflows,
                                                              List<?> plugins,
                                                              ModelConfig model,
                                                              List<Map<String, Object>> promptTemplate,
                                                              List<String> tools) {
        LegacyReActAgentConfig config = LLMAgentFactory.createLlmAgentConfig(
                agentId,
                agentVersion,
                description,
                null,
                null,
                model,
                promptTemplate,
                tools
        );
        config.setWorkflows(workflows);
        config.setPlugins(plugins);
        return config;
    }

    public static LlmAgent createLlmAgent(LegacyReActAgentConfig agentConfig) {
        return createLlmAgent(agentConfig, null, null);
    }

    public static LlmAgent createLlmAgent(LegacyReActAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        LlmAgent agent = new LlmAgent(agentConfig);
        agent.addWorkflows(workflows);
        agent.addTools(tools == null ? List.of() : tools);
        return agent;
    }
}
