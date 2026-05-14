/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal delegated task tool for configured subagents.
 *
 * <p>Mirrors Python's task delegation flow in
 * {@code openjiuwen.harness.rails.subagent.subagent_rail}.
 */
public class TaskTool extends AbstractHarnessTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeepAgent parentAgent;

    public TaskTool(DeepAgent parentAgent) {
        super(toolCard("harness.task", "task", "Delegate a task to a configured subagent."), null);
        this.parentAgent = parentAgent;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Object rawSession = kwargs.get("session");
        if (!(rawSession instanceof com.openjiuwen.core.session.Session session)) {
            return new ToolOutput(false, null, "task tool requires valid session");
        }
        String agentName = stringValue(inputs.get("subagent_type"));
        if (agentName.isBlank()) {
            agentName = stringValue(inputs.get("agent_name"));
        }
        DeepAgent target = findSubagent(agentName);
        if (target == null) {
            return new ToolOutput(false, null, "subagent not found: " + agentName);
        }
        String description = stringValue(inputs.get("task_description"));
        if (description.isBlank()) {
            description = stringValue(inputs.get("description"));
        }
        String prompt = stringValue(inputs.get("prompt"));
        if (description.isBlank() && prompt.isBlank()) {
            return new ToolOutput(false, null, "required field missing: task_description or prompt");
        }
        String taskId = buildSubSessionId(session.getSessionId(), agentName);
        AgentSessionApi childSession = AgentSessionApi.create(taskId, null, target.getCard());
        if (parentAgent.getConfig() instanceof DeepAgentConfig config && config.getSessionToolkit() != null) {
            config.getSessionToolkit().register(childSession, safeCardValue(target.getCard(), "name"), description);
            config.getSessionToolkit().upsertTask(taskId, taskId, description, "running");
        }

        Map<String, Object> toolArgs = new LinkedHashMap<>();
        toolArgs.put("query", prompt.isBlank() ? description : prompt);
        toolArgs.put("conversation_id", taskId);

        Object result;
        if (parentAgent.getConfig() instanceof DeepAgentConfig config && config instanceof DeepAgentConfig) {
            result = prompt.isBlank() && description.isBlank()
                    ? null
                    : runSubagent(target, toolArgs, childSession);
        } else {
            result = runSubagent(target, toolArgs, childSession);
        }

        if (parentAgent.getConfig() instanceof DeepAgentConfig config && config.getSessionToolkit() != null) {
            config.getSessionToolkit().completeTask(taskId, result != null ? String.valueOf(result) : "");
        }
        if (result instanceof Map<?, ?> rawMap) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            typed.putIfAbsent("agent_id", safeCardValue(target.getCard(), "id"));
            return new ToolOutput(true, typed, null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", taskId);
        data.put("agent_name", safeCardValue(target.getCard(), "name"));
        data.put("result", result);
        return new ToolOutput(true, data, null);
    }

    private static String buildSubSessionId(String parentSessionId, String subagentType) {
        String normalizedType = subagentType == null ? "" : subagentType.trim();
        if ("browser_agent".equals(normalizedType) || "verification_agent".equals(normalizedType)) {
            return parentSessionId + "_sub_" + normalizedType;
        }
        return parentSessionId + "_sub_" + normalizedType + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    protected Object runSubagent(DeepAgent target, Map<String, Object> toolArgs, AgentSessionApi childSession) {
        return com.openjiuwen.core.runner.Runner.runAgent(target, toolArgs, childSession, null);
    }

    private DeepAgent findSubagent(String agentName) {
        if (parentAgent.getConfig() instanceof DeepAgentConfig config) {
            for (DeepAgent subagent : config.getSubagents()) {
                String name = safeCardValue(subagent.getCard(), "name");
                if (agentName.equals(name)) {
                    return subagent;
                }
            }
        }
        return null;
    }

    private static String serialize(Map<String, Object> toolArgs) {
        try {
            return OBJECT_MAPPER.writeValueAsString(toolArgs);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String safeCardValue(Object card, String fieldName) {
        Object value = readField(card, fieldName);
        return value != null ? String.valueOf(value) : "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
