/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-level compatibility exports for the legacy single-agent API.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.single_agent.legacy} module in
 * {@code openjiuwen/core/single_agent/legacy/__init__.py}.</p>
 */
public final class LegacyPackage {
    public static final String PYTHON_MODULE = "openjiuwen/core/single_agent/legacy/__init__.py";

    private static final Map<String, String> EXPORTS = new LinkedHashMap<>();

    static {
        EXPORTS.put("LegacyReActAgent", "com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgent");
        EXPORTS.put("create_react_agent_config",
                "com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgentFactory.createReactAgentConfig");
        EXPORTS.put("LegacyBaseAgent", "com.openjiuwen.core.singleagent.legacy.agent.BaseAgent");
        EXPORTS.put("ControllerAgent", "com.openjiuwen.core.singleagent.legacy.agent.ControllerAgent");
        EXPORTS.put("WorkflowFactory", "com.openjiuwen.core.singleagent.legacy.agent.WorkflowFactory");
        EXPORTS.put("workflow_provider", "com.openjiuwen.core.singleagent.legacy.agent.WorkflowFactory.workflowProvider");
        EXPORTS.put("AgentConfig", "com.openjiuwen.core.singleagent.legacy.config.AgentConfig");
        EXPORTS.put("LLMCallConfig", "com.openjiuwen.core.singleagent.legacy.config.LlmCallConfig");
        EXPORTS.put("IntentDetectionConfig",
                "com.openjiuwen.core.singleagent.legacy.config.IntentDetectionConfig");
        EXPORTS.put("ConstrainConfig", "com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig");
        EXPORTS.put("DefaultResponse", "com.openjiuwen.core.controller.config.DefaultResponse");
        EXPORTS.put("WorkflowAgentConfig", "com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig");
        EXPORTS.put("MemoryConfig", "com.openjiuwen.core.singleagent.legacy.config.MemoryConfig");
        EXPORTS.put("LegacyReActAgentConfig",
                "com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig");
        EXPORTS.put("WorkflowSchema", "com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema");
        EXPORTS.put("PluginSchema", "com.openjiuwen.core.singleagent.legacy.schema.PluginSchema");
    }

    private LegacyPackage() {
    }

    public static List<String> exports() {
        return List.copyOf(EXPORTS.keySet());
    }

    public static String resolveExport(String name) {
        return EXPORTS.get(name);
    }
}
