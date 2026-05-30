/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal claim/complete task tool.
 *
 * <p>Mirrors Python's {@code ClaimTaskTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ClaimTaskTool extends TeamTool {

    public ClaimTaskTool(TeamBackend team) {
        super(toolCard("team.claim_task", "claim_task", "Claim or complete a task.", Map.of(
                "task_id", stringParam("Task id"),
                "status", stringParam("claimed or completed")
        ), List.of("task_id", "status")), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String taskId = stringValue(inputs.get("task_id"));
        String status = stringValue(inputs.get("status"));
        if (status.isBlank()) {
            status = "claimed";
        }
        if (taskId.isBlank()) {
            return new TeamToolOutput(false, null, "task_id is required");
        }
        var before = team.getTask(taskId);
        if (before == null) {
            return new TeamToolOutput(false, null, "Task not found");
        }
        String from = before.getStatus().name().toLowerCase();
        boolean success;
        if ("completed".equalsIgnoreCase(status)) {
            success = team.completeTask(taskId);
        } else {
            success = team.claimTask(taskId, team.getMemberName());
            status = "claimed";
        }
        if (!success) {
            return new TeamToolOutput(false, null, "Task not found or operation failed");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", taskId);
        data.put("status", status.toLowerCase());
        data.put("updated_fields", List.of("status"));
        data.put("status_change", Map.of("from", from, "to", status.toLowerCase()));
        return new TeamToolOutput(true, data, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public String mapResult(TeamToolOutput output) {
        if (!output.isSuccess() || !(output.getData() instanceof Map<?, ?> data)) {
            return super.mapResult(output);
        }
        Object changeObj = data.get("status_change");
        String text = "Task #" + data.get("task_id");
        if (changeObj instanceof Map<?, ?> change) {
            text += " " + change.get("from") + " \u2192 " + change.get("to");
            if ("completed".equals(change.get("to"))) {
                text += ". Use view_task to inspect remaining work.";
            }
        }
        return text;
    }
}
