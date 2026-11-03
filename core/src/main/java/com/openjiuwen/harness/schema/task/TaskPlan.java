/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Structured task plan for the outer task loop.
 *
 * <p>Mirrors Python's {@code TaskPlan} in
 * {@code openjiuwen/harness/schema/task.py}.
 */
public class TaskPlan {

    private String goal;
    private final List<TodoItem> tasks;
    private String currentTaskId;

    public TaskPlan() {
        this("", List.of(), null);
    }

    public TaskPlan(String goal) {
        this(goal, List.of(), null);
    }

    public TaskPlan(String goal, List<TodoItem> tasks) {
        this(goal, tasks, null);
    }

    public TaskPlan(String goal, List<TodoItem> tasks, String currentTaskId) {
        this.goal = goal == null ? "" : goal;
        this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        this.currentTaskId = currentTaskId;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal == null ? "" : goal;
    }

    public List<TodoItem> getTasks() {
        return tasks;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public TodoItem getTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        for (TodoItem task : tasks) {
            if (taskId.equals(task.getId())) {
                return task;
            }
        }
        return null;
    }

    public TodoItem getNextTask() {
        LinkedHashSet<String> doneIds = new LinkedHashSet<>();
        for (TodoItem task : tasks) {
            if (task.getStatus() == TodoStatus.COMPLETED || task.getStatus() == TodoStatus.CANCELLED) {
                doneIds.add(task.getId());
            }
        }
        for (TodoItem task : tasks) {
            if (task.getStatus() != TodoStatus.PENDING) {
                continue;
            }
            if (doneIds.containsAll(task.getDependsOn())) {
                return task;
            }
        }
        return null;
    }

    public void addTask(TodoItem task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    public void markInProgress(String taskId) {
        TodoItem task = getTask(taskId);
        if (task != null) {
            task.setStatus(TodoStatus.IN_PROGRESS);
            currentTaskId = taskId;
        }
    }

    public void markCompleted(String taskId) {
        markCompleted(taskId, "");
    }

    public void markCompleted(String taskId, String summary) {
        TodoItem task = getTask(taskId);
        if (task != null) {
            task.setStatus(TodoStatus.COMPLETED);
            task.setResultSummary(summary);
            if (taskId.equals(currentTaskId)) {
                currentTaskId = null;
            }
        }
    }

    public void markCancelled(String taskId) {
        markCancelled(taskId, "");
    }

    public void markCancelled(String taskId, String reason) {
        TodoItem task = getTask(taskId);
        if (task != null) {
            task.setStatus(TodoStatus.CANCELLED);
            task.setResultSummary(reason);
            if (taskId.equals(currentTaskId)) {
                currentTaskId = null;
            }
        }
    }

    public String getProgressSummary() {
        long done = tasks.stream().filter(task -> task.getStatus() == TodoStatus.COMPLETED).count();
        return done + "/" + tasks.size() + " completed";
    }

    public String toMarkdown() {
        List<String> lines = new ArrayList<>();
        lines.add("## Goal: " + goal);
        lines.add("");
        for (TodoItem task : tasks) {
            String mark = switch (task.getStatus()) {
                case COMPLETED -> "\u221a";
                case IN_PROGRESS -> ">";
                case CANCELLED -> "\u00d7";
                default -> " ";
            };
            String suffix = task.getResultSummary() == null || task.getResultSummary().isEmpty()
                    ? ""
                    : " \u2014 " + task.getResultSummary();
            lines.add("- [" + mark + "] " + task.getContent() + suffix);
        }
        return String.join("\n", lines);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("goal", goal);
        map.put("tasks", tasks.stream().map(TodoItem::toMap).toList());
        map.put("current_task_id", currentTaskId);
        return map;
    }

    public static TaskPlan fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new TaskPlan();
        }

        String goal = map.get("goal") == null ? "" : String.valueOf(map.get("goal"));
        List<TodoItem> tasks = new ArrayList<>();
        Object rawTasks = map.get("tasks");
        if (rawTasks instanceof Iterable<?> items) {
            for (Object item : items) {
                if (item instanceof TodoItem todoItem) {
                    tasks.add(todoItem);
                } else if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> taskMap = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        taskMap.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    tasks.add(TodoItem.fromMap(taskMap));
                }
            }
        }

        String currentTaskId = map.get("current_task_id") == null ? null : String.valueOf(map.get("current_task_id"));
        return new TaskPlan(goal, tasks, currentTaskId);
    }
}
