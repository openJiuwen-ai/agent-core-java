/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Minimal unified task-viewing tool.
 *
 * <p>Mirrors Python's {@code ViewTaskToolV2} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ViewTaskTool extends TeamTool {

    public ViewTaskTool(TeamBackend team) {
        super(toolCard("team.view_task", "view_task", "View one task or list tasks.", Map.of(
                "action", stringParam("list, get, or claimable"),
                "task_id", stringParam("Task id for get"),
                "status", stringParam("Status filter for list")
        ), List.of()), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String action = stringValue(inputs.get("action"));
        if (action.isBlank()) {
            action = "list";
        }
        if ("get".equals(action)) {
            String taskId = stringValue(inputs.get("task_id"));
            if (taskId.isBlank()) {
                return new TeamToolOutput(false, null, "task_id required for get action");
            }
            TaskDetail detail = team.getTask(taskId);
            if (detail == null) {
                return new TeamToolOutput(false, null, "Task not found");
            }
            return new TeamToolOutput(true, detailMap(detail), null);
        }

        TaskStatus status = "claimable".equals(action) ? TaskStatus.PENDING : parseStatus(stringValue(inputs.get("status")));
        List<TaskSummary> tasks = team.listTasks(status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks.stream().map(ViewTaskTool::summaryMap).toList());
        data.put("count", tasks.size());
        return new TeamToolOutput(true, data, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public String mapResult(TeamToolOutput output) {
        if (!output.isSuccess() || !(output.getData() instanceof Map<?, ?> data)) {
            return super.mapResult(output);
        }
        if (data.get("tasks") instanceof List<?> tasks) {
            List<String> lines = new ArrayList<>();
            for (Object item : tasks) {
                if (item instanceof Map<?, ?> task) {
                    StringBuilder line = new StringBuilder();
                    line.append("#").append(task.get("task_id"))
                            .append(" [").append(task.get("status")).append("] ")
                            .append(task.get("title"));
                    Object assignee = task.get("assignee");
                    if (assignee != null) {
                        line.append(" (").append(assignee).append(")");
                    }
                    Object blockedBy = task.get("blocked_by");
                    if (blockedBy instanceof List<?> deps && !deps.isEmpty()) {
                        line.append(" [blocked by ").append(formatTaskRefs(deps)).append("]");
                    }
                    lines.add(line.toString());
                }
            }
            return String.join("\n", lines);
        }
        StringBuilder text = new StringBuilder();
        text.append("Task #").append(data.get("task_id")).append(": ").append(data.get("title"));
        if (data.get("content") != null) {
            text.append("\nContent: ").append(data.get("content"));
        }
        Object blocks = data.get("blocks");
        if (blocks instanceof List<?> list && !list.isEmpty()) {
            text.append("\nBlocks: ").append(formatTaskRefs(list));
        }
        return text.toString();
    }

    private static Map<String, Object> summaryMap(TaskSummary summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", summary.getTaskId());
        data.put("title", summary.getTitle());
        data.put("status", summary.getStatus().name().toLowerCase());
        data.put("assignee", summary.getAssignee());
        data.put("blocked_by", summary.getBlockedBy());
        return data;
    }

    private static Map<String, Object> detailMap(TaskDetail detail) {
        Map<String, Object> data = summaryMap(detail);
        data.put("content", detail.getContent());
        data.put("blocks", detail.getBlocks());
        data.put("updated_at", detail.getUpdatedAt());
        return data;
    }

    private static TaskStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String formatTaskRefs(List<?> ids) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Object id : ids) {
            joiner.add("#" + id);
        }
        return joiner.toString();
    }
}
