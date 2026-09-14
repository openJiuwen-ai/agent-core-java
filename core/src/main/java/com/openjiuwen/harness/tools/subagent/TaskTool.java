/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Single-shot task spawning tool.
 *
 * <p>Mirrors Python's {@code TaskTool} and {@code create_task_tool} in
 * {@code openjiuwen/harness/tools/subagent/task_tool.py}.</p>
 */
public class TaskTool extends AbstractHarnessTool {

    private final DeepAgent parentAgent;
    private final String language;
    private final TaskRunner taskRunner;

    public TaskTool(TaskRunner taskRunner) {
        super(toolCard("task_tool", "task_tool", "Run a subagent task."));
        this.parentAgent = null;
        this.language = "cn";
        this.taskRunner = taskRunner;
    }

    public TaskTool(ToolCard card, DeepAgent parentAgent) {
        this(card, parentAgent, "cn");
    }

    public TaskTool(ToolCard card, DeepAgent parentAgent, String language) {
        super(card == null ? toolCard("task_tool", "task_tool", "Run a subagent task.") : card);
        this.parentAgent = parentAgent;
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.taskRunner = null;
    }

    public static String buildSubSessionId(String parentSessionId, String subagentType) {
        String normalizedType = stringValue(subagentType).trim();
        if ("browser_agent".equals(normalizedType) || "verification_agent".equals(normalizedType)) {
            return parentSessionId + "_sub_" + normalizedType;
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return parentSessionId + "_sub_" + normalizedType + "_" + suffix;
    }

    public static List<Tool> createTaskTool(DeepAgent parentAgent, String availableAgents, String language) {
        return createTaskTool(parentAgent, availableAgents, language, null);
    }

    public static List<Tool> createTaskTool(DeepAgent parentAgent, String availableAgents, String language,
                                            String agentId) {
        String resolvedId = agentId == null || agentId.isBlank() ? "task_tool" : agentId + "_task_tool";
        ToolCard card = toolCard("task_tool", "task_tool", "Run a subagent task.");
        card.setId(resolvedId);
        return new ArrayList<>(List.of(new TaskTool(card, parentAgent, language)));
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        if (parentAgent == null) {
            return invokeLegacyRunner(inputs, kwargs);
        }
        Object session = kwargs == null ? null : kwargs.get("session");
        if (!(session instanceof AgentSessionApi parentSession)) {
            throw new IllegalArgumentException("TaskTool requires a valid session in kwargs");
        }
        String subagentType = requiredString(inputs, "subagent_type");
        String taskDescription = requiredString(inputs, "task_description");
        String subSessionId = buildSubSessionId(parentSession.getSessionId(), subagentType);

        DeepAgent subagent;
        try {
            subagent = parentAgent.createSubagent(subagentType, subSessionId);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Subagent " + subagentType + " creation failed: " + exception.getMessage(),
                    exception
            );
        }

        try {
            Map<String, Object> result = subagent.invoke(linkedMap(
                    "query", taskDescription,
                    "conversation_id", subSessionId
            ));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("output", stringValue(result == null ? null : result.getOrDefault("output", "")));
            data.put("agent_id", subagent.getCard().getId());
            return ToolOutput.success(data);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException(
                    "Subagent " + subagentType + " execution failed: " + cause.getMessage(),
                    cause
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Subagent " + subagentType + " execution failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private Object invokeLegacyRunner(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String prompt = requiredString(inputs, "prompt");
        String description = stringValue(inputs == null ? null : inputs.get("description"));
        if (taskRunner == null) {
            return ToolOutput.failure("task runner is not configured");
        }
        return ToolOutput.success(taskRunner.run(description, prompt, inputs == null ? Map.of() : inputs,
                kwargs == null ? Map.of() : kwargs));
    }

    /**
     * Java boundary for Python's subagent task execution in
     * {@code openjiuwen/harness/tools/subagent/task_tool.py}.
     */
    @FunctionalInterface
    public interface TaskRunner {
        Map<String, Object> run(String description, String prompt, Map<String, Object> inputs,
                                Map<String, Object> kwargs) throws Exception;
    }
}
