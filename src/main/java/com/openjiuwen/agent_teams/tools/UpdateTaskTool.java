/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal update-task tool.
 *
 * <p>Mirrors Python's {@code UpdateTaskTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class UpdateTaskTool extends TeamTool {

    public UpdateTaskTool(TeamBackend team) {
        super(toolCard("team.update_task", "update_task", "Update, cancel, or reassign a task.", Map.of(
                "task_id", stringParam("Task id"),
                "status", stringParam("New status"),
                "title", stringParam("New title"),
                "content", stringParam("New content"),
                "assignee", stringParam("Assignee"),
                "add_blocked_by", arrayParam("Dependencies to add")
        ), List.of("task_id")), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String taskId = stringValue(inputs.get("task_id"));
        if (taskId.isBlank()) {
            return new TeamToolOutput(false, null, "'task_id' is required");
        }
        String status = stringValue(inputs.get("status"));
        String title = stringValue(inputs.get("title"));
        String content = stringValue(inputs.get("content"));
        String assignee = stringValue(inputs.get("assignee"));
        List<String> addBlockedBy = stringList(inputs.get("add_blocked_by"));

        if ("*".equals(taskId) && "cancelled".equalsIgnoreCase(status)) {
            return new TeamToolOutput(true, Map.of("cancelled_count", team.cancelAllTasks()), null);
        }

        List<String> updatedFields = new ArrayList<>();
        if ("cancelled".equalsIgnoreCase(status)) {
            if (!team.cancelTask(taskId)) {
                return new TeamToolOutput(false, null, "Failed to cancel task");
            }
            return new TeamToolOutput(true, Map.of("task_id", taskId, "status", "cancelled"), null);
        }
        if (!title.isBlank() || !content.isBlank()) {
            if (!team.updateTask(taskId, title, content)) {
                return new TeamToolOutput(false, null, "Failed to update task");
            }
            if (!title.isBlank()) {
                updatedFields.add("title");
            }
            if (!content.isBlank()) {
                updatedFields.add("content");
            }
        }
        if (!assignee.isBlank()) {
            if (!team.assignTask(taskId, assignee)) {
                return new TeamToolOutput(false, null, "Failed to assign task");
            }
            updatedFields.add("assignee");
        }
        if (!addBlockedBy.isEmpty()) {
            if (!team.addBlockedBy(taskId, addBlockedBy)) {
                return new TeamToolOutput(false, null, "Circular dependency detected");
            }
            updatedFields.add("blocked_by");
        }
        if (updatedFields.isEmpty()) {
            return new TeamToolOutput(false, null,
                    "No update specified: provide status, title, content, assignee, or add_blocked_by");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", taskId);
        data.put("status", "updated");
        data.put("updated_fields", updatedFields);
        return new TeamToolOutput(true, data, null);
    }
}
