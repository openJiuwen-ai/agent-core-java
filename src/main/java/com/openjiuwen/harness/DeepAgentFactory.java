/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for DeepAgent construction.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/harness/factory.py}.</p>
 */
public final class DeepAgentFactory {

    private DeepAgentFactory() {
    }

    public static DeepAgent createDeepAgent(Object model) {
        return createDeepAgent(model, List.of(), Map.of());
    }

    public static DeepAgent createDeepAgent(
            Object model,
            List<Tool> tools,
            Map<String, DeepAgentConfig.SubAgentConfig> subagents
    ) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        config.setTools(normalizeTools(tools));
        config.setSubagents(injectGeneralPurposeSubagent(subagents));
        AgentCard card = new AgentCard("deep_agent", "deep_agent", "DeepAgent");
        DeepAgent agent = new DeepAgent(card);
        agent.configure(config);
        return agent;
    }

    public static List<Tool> normalizeTools(List<Tool> tools) {
        return tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }

    public static Map<String, DeepAgentConfig.SubAgentConfig> injectGeneralPurposeSubagent(
            Map<String, DeepAgentConfig.SubAgentConfig> subagents
    ) {
        Map<String, DeepAgentConfig.SubAgentConfig> result = new LinkedHashMap<>();
        if (subagents != null) {
            result.putAll(subagents);
        }
        result.putIfAbsent(
                "general-purpose",
                new DeepAgentConfig.SubAgentConfig(
                        "general-purpose",
                        "General purpose subagent",
                        "You are a general-purpose subagent."
                )
        );
        return result;
    }

    public static boolean isDisabledFreeSearchTool(Tool tool) {
        return tool != null
                && tool.getCard() != null
                && "free_search".equals(tool.getCard().getName());
    }
}
