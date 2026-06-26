/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deprecation metadata used by legacy single-agent compatibility classes.
 *
 * <p>Mirrors Python's {@code _deprecated_class} helper in
 * {@code openjiuwen/core/single_agent/legacy/__init__.py}.</p>
 */
public final class LegacyDeprecation {
    public static final String PYTHON_MODULE = "openjiuwen/core/single_agent/legacy/__init__.py";

    private static final Map<String, String> ALTERNATIVES = new LinkedHashMap<>();
    private static final Set<String> WRAPPED_CLASS_NAMES = ConcurrentHashMap.newKeySet();

    static {
        ALTERNATIVES.put("LegacyBaseAgent", "openjiuwen.core.single_agent.agent.BaseAgent");
        ALTERNATIVES.put("ControllerAgent", "openjiuwen.core.single_agent.agent.BaseAgent");
        ALTERNATIVES.put("WorkflowFactory", "Workflow class directly");
        ALTERNATIVES.put("AgentConfig", "AgentCard + ReActAgentConfig");
        ALTERNATIVES.put("LLMCallConfig", "ReActAgentConfig");
        ALTERNATIVES.put("IntentDetectionConfig", "new config classes");
        ALTERNATIVES.put("ConstrainConfig", "ReActAgentConfig");
        ALTERNATIVES.put("DefaultResponse", "new config classes");
        ALTERNATIVES.put("WorkflowAgentConfig", "WorkflowAgentConfig from workflow module");
        ALTERNATIVES.put("MemoryConfig", "MemoryScopeConfig");
        ALTERNATIVES.put("LegacyReActAgentConfig",
                "openjiuwen.core.single_agent.agents.react_agent.ReActAgentConfig");
        ALTERNATIVES.put("LegacyReActAgent",
                "openjiuwen.core.single_agent.agents.react_agent.ReActAgent");
        ALTERNATIVES.put("WorkflowSchema", "WorkflowCard");
        ALTERNATIVES.put("PluginSchema", "Tool class directly");
    }

    private LegacyDeprecation() {
    }

    public static String alternativeFor(String className) {
        return ALTERNATIVES.get(className);
    }

    public static String warningMessage(String className) {
        String alternative = ALTERNATIVES.get(className);
        if (alternative == null) {
            return null;
        }
        return className + " is deprecated and will be removed in the future. Please use "
                + alternative + " instead.";
    }

    public static boolean registerDeprecatedClass(String className) {
        if (!ALTERNATIVES.containsKey(className)) {
            return false;
        }
        return WRAPPED_CLASS_NAMES.add(className);
    }

    public static boolean isDeprecatedWrapped(String className) {
        return WRAPPED_CLASS_NAMES.contains(className);
    }
}
