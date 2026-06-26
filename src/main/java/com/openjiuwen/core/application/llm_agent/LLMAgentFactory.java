/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.PluginSchema;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.Map;

/**
 * Backward-compatible LLMAgent factory functions.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.</p>
 */
public final class LLMAgentFactory {
    private LLMAgentFactory() {
    }

    public static LegacyReActAgentConfig createLlmAgentConfig(String agentId,
                                                              String agentVersion,
                                                              String description,
                                                              List<WorkflowSchema> workflows,
                                                              List<PluginSchema> plugins,
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
                                                              List<WorkflowSchema> workflows,
                                                              List<PluginSchema> plugins,
                                                              ModelConfig model,
                                                              List<Map<String, Object>> promptTemplate,
                                                              List<String> tools) {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setWorkflows(workflows);
        config.setPlugins(plugins);
        config.setModel(model);
        config.setPromptTemplate(promptTemplate);
        config.setTools(tools == null ? List.of() : tools);
        return config;
    }

    public static LLMAgent createLlmAgent(LegacyReActAgentConfig agentConfig) {
        return createLlmAgent(agentConfig, null, null);
    }

    public static LLMAgent createLlmAgent(LegacyReActAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        LLMAgent agent = new LLMAgent(agentConfig);
        agent.addWorkflows(workflows);
        agent.addTools(tools == null ? List.of() : tools);
        return agent;
    }
}
