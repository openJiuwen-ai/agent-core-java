/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.SessionToolkit;
import com.openjiuwen.harness.tools.SessionsCancelTool;
import com.openjiuwen.harness.tools.SessionsListTool;
import com.openjiuwen.harness.tools.TaskTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public class SessionRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SessionRail extends DeepAgentRail {
    private final List<Tool> tools = new ArrayList<>();
    private SessionToolkit toolkit;

    /**
     * Auto-generated for codecheck compliance.
     */
    public int priority() {
        return 95;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void init(DeepAgent deepAgent) {
        if (deepAgent == null
                || deepAgent.getConfig() == null
                || deepAgent.getConfig().getSubagents() == null
                || deepAgent.getConfig().getSubagents().isEmpty()) {
            return;
        }
        toolkit = new SessionToolkit();
        deepAgent.setSessionToolkit(toolkit);
        SessionsListTool listTool = new SessionsListTool(toolkit);
        SessionsCancelTool cancelTool = new SessionsCancelTool(toolkit);
        TaskTool taskTool = new TaskTool(deepAgent);
        String language = deepAgent.getWorkspace().getLanguage();
        tools.add(new LocalFunction(
                card("sessions_list", deepAgent, language),
                inputs -> listTool.list()
        ));
        tools.add(new LocalFunction(
                card("sessions_cancel", deepAgent, language),
                inputs -> cancelTool.cancel(stringValue(inputs.get("task_id")))
        ));
        tools.add(new LocalFunction(
                card("sessions_spawn", deepAgent, language),
                inputs -> spawn(taskTool, inputs)
        ));
        for (Tool tool : tools) {
            deepAgent.registerHarnessTool(tool);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void uninit(DeepAgent deepAgent) {
        if (deepAgent != null) {
            for (Tool tool : tools) {
                deepAgent.unregisterHarnessTool(tool);
            }
            deepAgent.setSessionToolkit(null);
        }
        tools.clear();
        toolkit = null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String sessionScope(String sessionId) {
        return sessionId != null ? sessionId : "default";
    }

    private ToolOutput spawn(TaskTool taskTool, Map<String, Object> inputs) {
        String taskId = UUID.randomUUID().toString();
        String description = stringValue(inputs.getOrDefault("task_description", inputs.get("task")));
        ToolOutput output = taskTool.delegate(
                stringValue(inputs.get("subagent_type")),
                description,
                stringValue(inputs.get("parent_session_id"))
        );
        String subSessionId = "";
        if (output.getData() instanceof Map<?, ?> payload && payload.get("sub_session_id") != null) {
            subSessionId = String.valueOf(payload.get("sub_session_id"));
        }
        toolkit.upsertRunning(taskId, subSessionId, description);
        if (output.isSuccess()) {
            toolkit.markCompleted(taskId, String.valueOf(output.getData()));
        } else {
            toolkit.markFailed(taskId, output.getError());
        }
        return ToolOutput.builder()
                .success(output.isSuccess())
                .data(Map.of("task_id", taskId, "sub_session_id", subSessionId, "result", output.getData()))
                .error(output.getError())
                .build();
    }

    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
