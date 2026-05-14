/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spawns a delegated session entry for a configured subagent.
 *
 * <p>Mirrors Python's delegated session-spawn behaviors in
 * {@code openjiuwen.harness.rails.subagent.session_rail}.
 */
public class SessionsSpawnTool extends AbstractHarnessTool {

    private final DeepAgent parentAgent;
    private final DeepAgentConfig.SessionToolkit toolkit;

    public SessionsSpawnTool(DeepAgent parentAgent, DeepAgentConfig.SessionToolkit toolkit) {
        super(toolCard("harness.sessions.spawn", "sessions_spawn", "Create a delegated subagent session entry."), null);
        this.parentAgent = parentAgent;
        this.toolkit = toolkit;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String agentName = stringValue(inputs.get("agent_name"));
        if (agentName.isBlank()) {
            agentName = stringValue(inputs.get("subagent_type"));
        }
        String prompt = stringValue(inputs.get("prompt"));
        String description = stringValue(inputs.get("description"));
        DeepAgent target = findSubagent(agentName);
        if (target == null) {
            return new ToolOutput(false, null, "subagent not found: " + agentName);
        }
        String sessionId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        AgentSessionApi session = AgentSessionApi.create(sessionId, null, target.getCard());
        if (toolkit != null) {
            toolkit.register(session, safeCardValue(target.getCard(), "name"), prompt);
            toolkit.upsertTask(taskId, sessionId, description.isBlank() ? prompt : description, "running");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", taskId);
        data.put("session_id", sessionId);
        data.put("agent_name", safeCardValue(target.getCard(), "name"));
        data.put("status", "spawned");
        return new ToolOutput(true, data, null);
    }

    private DeepAgent findSubagent(String agentName) {
        if (parentAgent.getConfig() instanceof com.openjiuwen.harness.DeepAgentConfig config) {
            for (DeepAgent subagent : config.getSubagents()) {
                String name = safeCardValue(subagent.getCard(), "name");
                if (agentName.equals(name)) {
                    return subagent;
                }
            }
        }
        return null;
    }

    private static String safeCardValue(Object card, String fieldName) {
        Object value = readField(card, fieldName);
        return value != null ? String.valueOf(value) : "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
