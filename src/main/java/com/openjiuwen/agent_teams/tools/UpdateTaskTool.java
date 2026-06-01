/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;

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

        TaskDetail task = team.getTask(taskId);
        if (task == null) {
            return new TeamToolOutput(false, null, "Task not found");
        }

        List<String> updatedFields = new ArrayList<>();
        if ("cancelled".equalsIgnoreCase(status)) {
            if (isHumanAgentLocked(task)) {
                return new TeamToolOutput(false, null,
                        "Task " + taskId + " is claimed by a human member and cannot be cancelled");
            }
            cancelMemberIfClaimed(task);
            if (!team.cancelTask(taskId)) {
                return new TeamToolOutput(false, null, "Failed to cancel task");
            }
            return new TeamToolOutput(true, Map.of("task_id", taskId, "status", "cancelled"), null);
        }
        if (!title.isBlank() || !content.isBlank()) {
            cancelMemberIfClaimed(task);
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
            String currentAssignee = task.getAssignee();
            if (currentAssignee != null && !currentAssignee.isBlank() && !currentAssignee.equals(assignee)) {
                if (isHumanAgentLocked(task)) {
                    return new TeamToolOutput(false, null,
                            "Task " + taskId + " is claimed by a human member and cannot be reassigned to " + assignee);
                }
                team.cancelMember(currentAssignee);
            }
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

    private boolean isHumanAgentLocked(TaskDetail task) {
        return task != null
                && task.getStatus() == TaskStatus.CLAIMED
                && team.isHumanAgent(task.getAssignee());
    }

    private void cancelMemberIfClaimed(TaskDetail task) {
        if (task == null || task.getStatus() != TaskStatus.CLAIMED) {
            return;
        }
        String assignee = task.getAssignee();
        if (assignee != null && !assignee.isBlank() && !team.isHumanAgent(assignee)) {
            team.cancelMember(assignee);
        }
    }
}
