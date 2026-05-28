/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tasks with group-based lookup and auto-cleanup.
 * <p>
 * Mirrors Python's {@code TaskRegistry} class from
 * <code>common/task_manager/registry.py</code>.
 */
public class TaskRegistry {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    public void add(Task task) {
        tasks.put(task.getTaskId(), task);
        if (task.getGroup() != null) {
            groups.computeIfAbsent(task.getGroup(), k -> ConcurrentHashMap.newKeySet()).add(task.getTaskId());
        }
    }

    public Task get(String taskId) {
        return tasks.get(taskId);
    }

    public boolean contains(String taskId) {
        return tasks.containsKey(taskId);
    }

    public List<Task> getByGroup(String group) {
        Set<String> ids = groups.getOrDefault(group, Set.of());
        List<Task> result = new ArrayList<>();
        for (String id : ids) {
            Task t = tasks.get(id);
            if (t != null) result.add(t);
        }
        return result;
    }

    public List<Task> getByParent(String parentId) {
        List<Task> result = new ArrayList<>();
        for (Task t : tasks.values()) {
            if (parentId.equals(t.getParentTaskId())) {
                result.add(t);
            }
        }
        return result;
    }

    public List<Task> getByStatus(TaskStatus status) {
        List<Task> result = new ArrayList<>();
        for (Task t : tasks.values()) {
            if (t.getStatus() == status) result.add(t);
        }
        return result;
    }

    public List<Task> getRunning() {
        return getByStatus(TaskStatus.RUNNING);
    }

    public List<Task> getAll() {
        return new ArrayList<>(tasks.values());
    }

    public Set<String> keys() {
        return tasks.keySet();
    }

    public Task remove(String taskId) {
        Task task = tasks.remove(taskId);
        if (task != null && task.getGroup() != null) {
            Set<String> groupSet = groups.get(task.getGroup());
            if (groupSet != null) {
                groupSet.remove(taskId);
                if (groupSet.isEmpty()) {
                    groups.remove(task.getGroup());
                }
            }
        }
        return task;
    }

    public List<String> getGroupTaskIds(String group) {
        return new ArrayList<>(groups.getOrDefault(group, Set.of()));
    }
}
