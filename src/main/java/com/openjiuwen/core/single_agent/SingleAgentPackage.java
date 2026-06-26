/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-level exports for the current single-agent API.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.single_agent} module in
 * {@code openjiuwen/core/single_agent/__init__.py}.</p>
 */
public final class SingleAgentPackage {
    public static final String PYTHON_MODULE = "openjiuwen/core/single_agent/__init__.py";

    private static final Map<String, String> EXPORTS = new LinkedHashMap<>();

    static {
        EXPORTS.put("AgentCard", "com.openjiuwen.core.single_agent.schema.AgentCard");
        EXPORTS.put("ReActAgent", "com.openjiuwen.core.single_agent.agents.ReActAgent");
        EXPORTS.put("ReActAgentConfig", "com.openjiuwen.core.single_agent.agents.ReActAgentConfig");
        EXPORTS.put("ReActAgentEvolve", "com.openjiuwen.core.single_agent.agents.ReActAgentEvolve");
        EXPORTS.put("Session", "com.openjiuwen.core.session.AgentSession");
        EXPORTS.put("create_agent_session", "com.openjiuwen.core.session.AgentSession.createAgentSession");
        EXPORTS.put("BaseAgent", "com.openjiuwen.core.single_agent.BaseAgent");
        EXPORTS.put("AbilityManager", "com.openjiuwen.core.single_agent.AbilityManager");
        EXPORTS.put("LegacyBaseAgent", "com.openjiuwen.core.single_agent.legacy.LegacyBaseAgent");
        EXPORTS.put("AddAbilityResult", "com.openjiuwen.core.single_agent.AddAbilityResult");
    }

    private SingleAgentPackage() {
    }

    public static List<String> exports() {
        return List.copyOf(EXPORTS.keySet());
    }

    public static String resolveExport(String name) {
        return EXPORTS.get(name);
    }
}
