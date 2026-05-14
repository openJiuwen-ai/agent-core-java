/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.TaskRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal create-task tool.
 *
 * <p>Mirrors Python's {@code CreateTaskTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class CreateTaskTool extends TeamTool {

    public CreateTaskTool(TeamBackend team) {
        super(toolCard("team.create_task", "create_task", "Create one or more team tasks."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Object rawTasks = inputs.get("tasks");
        if (!(rawTasks instanceof List<?> list) || list.isEmpty()) {
            return new TeamToolOutput(false, null, "'tasks' is required");
        }

        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawSpec)) {
                failures.add(Map.of("spec", "<unknown>", "reason", "invalid task spec"));
                continue;
            }
            Map<String, Object> spec = new LinkedHashMap<>();
            rawSpec.forEach((k, v) -> spec.put(String.valueOf(k), v));
            String title = stringValue(spec.get("title"));
            String content = stringValue(spec.get("content"));
            if (title.isBlank() || content.isBlank()) {
                failures.add(Map.of("spec", specLabel(spec), "reason", "missing required title/content"));
                continue;
            }
            @SuppressWarnings("unchecked")
            List<String> dependsOn = spec.get("depends_on") instanceof List<?> deps ? (List<String>) deps : List.of();
            TaskRecord record = team.createTask(title, content, stringValue(spec.get("task_id")), dependsOn);
            created.add(taskBrief(record));
        }

        if (created.isEmpty() && !failures.isEmpty()) {
            return new TeamToolOutput(false, null, "All task creations failed");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", created);
        data.put("count", created.size());
        data.put("skipped", failures.size());
        data.put("failures", failures);
        return new TeamToolOutput(true, data, null);
    }

    private static String specLabel(Map<String, Object> spec) {
        String taskId = stringValue(spec.get("task_id"));
        return !taskId.isBlank() ? taskId : stringValue(spec.get("title"));
    }

    private static Map<String, Object> taskBrief(TaskRecord record) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("task_id", record.getTaskId());
        brief.put("title", record.getTitle());
        brief.put("status", record.getStatus().name().toLowerCase());
        return brief;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
