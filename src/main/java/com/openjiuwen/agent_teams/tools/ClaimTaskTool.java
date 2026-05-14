/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal claim/complete task tool.
 *
 * <p>Mirrors Python's {@code ClaimTaskTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ClaimTaskTool extends TeamTool {

    public ClaimTaskTool(TeamBackend team) {
        super(toolCard("team.claim_task", "claim_task", "Claim or complete a task."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String taskId = inputs.get("task_id") != null ? String.valueOf(inputs.get("task_id")) : "";
        String status = inputs.get("status") != null ? String.valueOf(inputs.get("status")) : "claimed";
        if (taskId.isBlank()) {
            return new TeamToolOutput(false, null, "task_id is required");
        }
        boolean success;
        Object runtimeResult = null;
        if ("completed".equalsIgnoreCase(status)) {
            success = team.completeTask(taskId);
        } else {
            Map<String, Object> claimResult = team.claimAndRunTask(taskId, team.getMemberName());
            success = claimResult != null;
            if (claimResult != null) {
                runtimeResult = claimResult.get("runtime_result");
            }
            status = "claimed";
        }
        if (!success) {
            return new TeamToolOutput(false, null, "Task not found or operation failed");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", taskId);
        data.put("status", status.toLowerCase());
        if (runtimeResult != null) {
            data.put("runtime_result", runtimeResult);
        }
        return new TeamToolOutput(true, data, null);
    }
}
