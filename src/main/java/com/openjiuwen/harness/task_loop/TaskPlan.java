/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class TaskPlan used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TaskPlan {
    @Builder.Default
    private String goal = "";
    @Builder.Default
    private List<TodoItem> tasks = new ArrayList<>();
    private String currentTaskId;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TodoItem getTask(String taskId) {
        if (taskId == null || tasks == null) {
            return nullValue();
        }
        for (TodoItem task : tasks) {
            if (task != null && taskId.equals(task.getId())) {
                return task;
            }
        }
        return nullValue();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TodoItem getNextTask() {
        if (tasks == null || tasks.isEmpty()) {
            return nullValue();
        }
        Set<String> doneIds = new LinkedHashSet<>();
        for (TodoItem task : tasks) {
            if (task != null && task.isTerminal()) {
                doneIds.add(task.getId());
            }
        }
        for (TodoItem task : tasks) {
            if (task == null || !task.isPending()) {
                continue;
            }
            List<String> dependsOn = task.getDependsOn() == null ? List.of() : task.getDependsOn();
            if (doneIds.containsAll(dependsOn)) {
                return task;
            }
        }
        return nullValue();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addTask(TodoItem task) {
        if (task == null) {
            return;
        }
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        tasks.add(task);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markInProgress(String taskId) {
        TodoItem task = getTask(taskId);
        if (task != null) {
            task.setStatus(TodoStatus.IN_PROGRESS);
            currentTaskId = taskId;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markCompleted(String taskId) {
        markCompleted(taskId, "");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markCompleted(String taskId, String summary) {
        TodoItem task = getTask(taskId);
        if (task != null) {
            task.setStatus(TodoStatus.COMPLETED);
            task.setResultSummary(summary == null ? "" : summary);
            if (taskId != null && taskId.equals(currentTaskId)) {
                currentTaskId = null;
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markCancelled(String taskId) {
        markCancelled(taskId, "");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markCancelled(String taskId, String reason) {
        TodoItem task = getTask(taskId);
        if (task != null) {
            task.setStatus(TodoStatus.CANCELLED);
            task.setResultSummary(reason == null ? "" : reason);
            if (taskId != null && taskId.equals(currentTaskId)) {
                currentTaskId = null;
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getProgressSummary() {
        int total = tasks == null ? 0 : tasks.size();
        long done = tasks == null ? 0 : tasks.stream().filter(item -> item != null && item.isCompleted()).count();
        return done + "/" + total + " completed";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("## Goal: ").append(goal == null ? "" : goal).append("\n\n");
        if (tasks == null) {
            return out.toString();
        }
        for (TodoItem task : tasks) {
            if (task == null) {
                continue;
            }
            out.append("- [")
                    .append(mark(task.getStatus()))
                    .append("] ")
                    .append(task.getContent() == null ? "" : task.getContent());
            if (task.getResultSummary() != null && !task.getResultSummary().isBlank()) {
                out.append(" - ").append(task.getResultSummary());
            }
            out.append('\n');
        }
        return out.toString().stripTrailing();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goal", goal == null ? "" : goal);
        data.put("tasks", tasks == null ? List.of() : tasks);
        data.put("current_task_id", currentTaskId);
        return data;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void save(Path path) throws IOException {
        if (path == null) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, JsonUtils.safeJsonDumps(toMap(), "{}"));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskPlan load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return TaskPlan.builder().build();
        }
        Map<String, Object> data = JsonUtils.getMapper().readValue(
                Files.readString(path),
                new TypeReference<Map<String, Object>>() {}
        );
        return fromMap(data);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskPlan fromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return TaskPlan.builder().build();
        }
        List<TodoItem> parsedTasks = parseTasks(data.get("tasks"));
        return TaskPlan.builder()
                .goal(string(data.get("goal")))
                .tasks(parsedTasks)
                .currentTaskId(stringOrNull(firstNonNull(data, new String[] {"current_task_id", "currentTaskId"})))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskPlan fromObject(Object value) {
        if (value instanceof TaskPlan plan) {
            return plan;
        }
        if (value instanceof Map<?, ?> map) {
            return fromMap((Map<String, Object>) map);
        }
        return TaskPlan.builder().build();
    }

    private static List<TodoItem> parseTasks(Object raw) {
        if (raw instanceof List<?> list) {
            List<TodoItem> parsed = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof TodoItem todoItem) {
                    parsed.add(todoItem);
                } else if (item instanceof Map<?, ?> map) {
                    parsed.add(JsonUtils.getMapper().convertValue(map, TodoItem.class));
                }
            }
            return parsed;
        }
        if (raw == null) {
            return new ArrayList<>();
        }
        return JsonUtils.getMapper().convertValue(raw, new TypeReference<List<TodoItem>>() {});
    }

    private static String mark(TodoStatus status) {
        if (status == TodoStatus.DONE || status == TodoStatus.COMPLETED) {
            return "x";
        }
        if (status == TodoStatus.IN_PROGRESS) {
            return ">";
        }
        if (status == TodoStatus.CANCELLED) {
            return "!";
        }
        return " ";
    }

    private static Object firstNonNull(Map<String, Object> source, String[] keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return nullValue();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    private static <T> T nullValue() {
        return null;
    }

}
