/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal in-memory team task manager.
 *
 * <p>Mirrors Python's {@code TeamTaskManager} in
 * {@code openjiuwen.agent_teams.tools.task_manager}.
 */
public class TeamTaskManager {

    private final String teamName;
    private final String memberName;
    private final Map<String, TaskRecord> tasks;

    public TeamTaskManager(String teamName, String memberName) {
        this(teamName, memberName, new LinkedHashMap<>());
    }

    public TeamTaskManager(String teamName, String memberName, Map<String, TaskRecord> tasks) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.tasks = tasks != null ? tasks : new LinkedHashMap<>();
    }

    public TaskRecord add(String title, String content, String taskId, List<String> dependencies) {
        String id = taskId != null && !taskId.isBlank() ? taskId : UUID.randomUUID().toString();
        TaskStatus status = dependencies != null && !dependencies.isEmpty() ? TaskStatus.BLOCKED : TaskStatus.PENDING;
        TaskRecord record = new TaskRecord(id, title, content, status);
        if (dependencies != null) {
            record.getBlockedBy().addAll(dependencies);
            for (String dep : dependencies) {
                TaskRecord upstream = tasks.get(dep);
                if (upstream != null && !upstream.getBlocks().contains(id)) {
                    upstream.getBlocks().add(id);
                }
            }
        }
        tasks.put(id, record);
        return record;
    }

    public List<TaskRecord> addBatch(List<Map<String, Object>> specs) {
        List<TaskRecord> created = new ArrayList<>();
        for (Map<String, Object> spec : specs) {
            String title = stringValue(spec.get("title"));
            String content = stringValue(spec.get("content"));
            if (title.isBlank() || content.isBlank()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<String> deps = spec.get("dependencies") instanceof List<?> list
                    ? (List<String>) list : List.of();
            created.add(add(title, content, stringValue(spec.get("task_id")), deps));
        }
        return created;
    }

    public List<TaskSummary> list() {
        return tasks.values().stream().map(TaskRecord::toSummary).toList();
    }

    public TaskDetail get(String taskId) {
        TaskRecord record = tasks.get(taskId);
        return record != null ? record.toDetail() : null;
    }

    public boolean claim(String taskId, String assignee) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        record.setAssignee(assignee != null && !assignee.isBlank() ? assignee : memberName);
        record.setStatus(TaskStatus.CLAIMED);
        return true;
    }

    public boolean complete(String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        record.setStatus(TaskStatus.COMPLETED);
        for (String blockedTaskId : record.getBlocks()) {
            TaskRecord blocked = tasks.get(blockedTaskId);
            if (blocked == null) {
                continue;
            }
            blocked.getBlockedBy().remove(taskId);
            if (blocked.getBlockedBy().isEmpty() && blocked.getStatus() == TaskStatus.BLOCKED) {
                blocked.setStatus(TaskStatus.PENDING);
            }
        }
        return true;
    }

    public boolean cancel(String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        record.setStatus(TaskStatus.CANCELLED);
        return true;
    }

    public boolean update(String taskId, String title, String content) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        if (title != null && !title.isBlank()) {
            record.setTitle(title);
        }
        if (content != null && !content.isBlank()) {
            record.setContent(content);
        }
        return true;
    }

    public String getTeamName() {
        return teamName;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
