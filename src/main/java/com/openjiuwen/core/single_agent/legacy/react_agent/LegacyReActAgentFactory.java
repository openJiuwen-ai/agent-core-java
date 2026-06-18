/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy.react_agent;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for the legacy ReAct agent module.
 *
 * <p>Mirrors Python's {@code create_react_agent_config} in
 * {@code openjiuwen/core/single_agent/legacy/react_agent.py}.</p>
 */
public final class LegacyReActAgentFactory {

    private LegacyReActAgentFactory() {
    }

    public static LegacyReActAgentConfig createReactAgentConfig(String agentId,
                                                                String agentVersion,
                                                                String description,
                                                                ModelConfig model,
                                                                List<Map<String, Object>> promptTemplate) {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setModel(model);
        config.setPromptTemplate(copyPrompt(promptTemplate));
        return config;
    }

    public static LegacyReActAgentConfig create_react_agent_config(String agentId,
                                                                   String agentVersion,
                                                                   String description,
                                                                   ModelConfig model,
                                                                   List<Map<String, Object>> promptTemplate) {
        return createReactAgentConfig(agentId, agentVersion, description, model, promptTemplate);
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
