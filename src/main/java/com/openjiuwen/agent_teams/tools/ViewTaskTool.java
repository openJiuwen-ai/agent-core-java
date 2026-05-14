/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal unified task-viewing tool.
 *
 * <p>Mirrors Python's {@code ViewTaskToolV2} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ViewTaskTool extends TeamTool {

    public ViewTaskTool(TeamBackend team) {
        super(toolCard("team.view_task", "view_task", "View one task or list tasks."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String action = inputs.get("action") != null ? String.valueOf(inputs.get("action")) : "list";
        if ("get".equals(action)) {
            String taskId = inputs.get("task_id") != null ? String.valueOf(inputs.get("task_id")) : "";
            if (taskId.isBlank()) {
                return new TeamToolOutput(false, null, "task_id required for get action");
            }
            TaskDetail detail = team.getTask(taskId);
            if (detail == null) {
                return new TeamToolOutput(false, null, "Task not found");
            }
            return new TeamToolOutput(true, detailMap(detail), null);
        }

        List<TaskSummary> tasks = team.listTasks();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks.stream().map(ViewTaskTool::summaryMap).toList());
        data.put("count", tasks.size());
        return new TeamToolOutput(true, data, null);
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
}
