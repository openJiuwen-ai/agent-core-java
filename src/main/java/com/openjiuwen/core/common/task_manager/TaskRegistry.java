/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for coroutine tasks.
 *
 * <p>Mirrors Python's {@code TaskRegistry} in
 * {@code openjiuwen/core/common/task_manager/registry.py}.</p>
 */
public class TaskRegistry {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    public void add(Task task) {
        tasks.put(task.getTaskId(), task);
        if (task.getGroup() != null) {
            groups.computeIfAbsent(task.getGroup(), ignored -> ConcurrentHashMap.newKeySet()).add(task.getTaskId());
        }
    }

    public Task get(String taskId) {
        return tasks.get(taskId);
    }

    public boolean contains(String taskId) {
        return tasks.containsKey(taskId);
    }

    public List<Task> getByGroup(String group) {
        Set<String> taskIds = groups.getOrDefault(group, Set.of());
        List<Task> result = new ArrayList<>();
        for (String taskId : taskIds) {
            Task task = tasks.get(taskId);
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    public List<Task> getByParent(String parentId) {
        List<Task> result = new ArrayList<>();
        for (Task task : tasks.values()) {
            if (parentId.equals(task.getParentTaskId())) {
                result.add(task);
            }
        }
        return result;
    }

    public List<Task> getByStatus(TaskStatus status) {
        List<Task> result = new ArrayList<>();
        for (Task task : tasks.values()) {
            if (task.getStatus() == status) {
                result.add(task);
            }
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
        return new LinkedHashSet<>(tasks.keySet());
    }

    public Collection<Task> values() {
        return List.copyOf(tasks.values());
    }

    public Set<Map.Entry<String, Task>> items() {
        return Set.copyOf(tasks.entrySet());
    }

    public Task pop(String taskId, Task defaultValue) {
        Task task = removeUnsafe(taskId);
        return task == null ? defaultValue : task;
    }

    public Task removeUnsafe(String taskId) {
        Task task = tasks.remove(taskId);
        if (task == null || task.getGroup() == null) {
            return task;
        }
        Set<String> groupTasks = groups.get(task.getGroup());
        if (groupTasks != null) {
            groupTasks.remove(taskId);
            if (groupTasks.isEmpty()) {
                groups.remove(task.getGroup());
            }
        }
        return task;
    }

    public Task remove(String taskId) {
        return removeUnsafe(taskId);
    }

    public List<String> getGroupTaskIds(String group) {
        return new ArrayList<>(groups.getOrDefault(group, Set.of()));
    }
}
