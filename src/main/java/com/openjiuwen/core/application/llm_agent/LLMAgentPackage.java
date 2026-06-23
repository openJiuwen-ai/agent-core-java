/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig;
import com.openjiuwen.core.singleagent.legacy.config.IntentDetectionConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.PluginSchema;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.Map;

/**
 * Package-level compatibility exports for the LLM agent application API.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.application.llm_agent} in
 * {@code openjiuwen/core/application/llm_agent/__init__.py}.</p>
 */
public final class LLMAgentPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/application/llm_agent/__init__.py";
    public static final List<String> ALL = List.of(
            "create_llm_agent_config",
            "create_llm_agent",
            "LLMAgent",
            "ConstrainConfig",
            "IntentDetectionConfig",
            "ReActAgentConfig"
    );
    public static final Class<LLMAgent> LLM_AGENT = LLMAgent.class;
    public static final Class<ConstrainConfig> CONSTRAIN_CONFIG = ConstrainConfig.class;
    public static final Class<IntentDetectionConfig> INTENT_DETECTION_CONFIG = IntentDetectionConfig.class;
    public static final Class<LegacyReActAgentConfig> REACT_AGENT_CONFIG = LegacyReActAgentConfig.class;

    private LLMAgentPackage() {
    }

    public static LegacyReActAgentConfig createLlmAgentConfig(String agentId,
                                                              String agentVersion,
                                                              String description,
                                                              List<WorkflowSchema> workflows,
                                                              List<PluginSchema> plugins,
                                                              ModelConfig model,
                                                              List<Map<String, Object>> promptTemplate) {
        return LLMAgentFactory.createLlmAgentConfig(
                agentId,
                agentVersion,
                description,
                workflows,
                plugins,
                model,
                promptTemplate
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
        return LLMAgentFactory.createLlmAgentConfig(
                agentId,
                agentVersion,
                description,
                workflows,
                plugins,
                model,
                promptTemplate,
                tools
        );
    }

    public static LLMAgent createLlmAgent(LegacyReActAgentConfig agentConfig) {
        return LLMAgentFactory.createLlmAgent(agentConfig);
    }

    public static LLMAgent createLlmAgent(LegacyReActAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        return LLMAgentFactory.createLlmAgent(agentConfig, workflows, tools);
    }
}
