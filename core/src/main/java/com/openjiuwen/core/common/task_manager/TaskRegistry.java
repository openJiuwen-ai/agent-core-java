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
import java.lang.ref.WeakReference;

/**
 * Registry for coroutine tasks.
 *
 * <p>Mirrors Python's {@code TaskRegistry} in
 * {@code openjiuwen/core/common/task_manager/registry.py}.</p>
 */
public class TaskRegistry {

    private final Map<String, WeakReference<Task>> tasks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    public void add(Task task) {
        tasks.put(task.getTaskId(), new WeakReference<>(task));
        if (task.getGroup() != null) {
            groups.computeIfAbsent(task.getGroup(), ignored -> ConcurrentHashMap.newKeySet()).add(task.getTaskId());
        }
    }

    public Task get(String taskId) {
        WeakReference<Task> reference = tasks.get(taskId);
        if (reference == null) {
            return null;
        }
        Task task = reference.get();
        if (task == null) {
            tasks.remove(taskId);
            removeTaskIdFromGroups(taskId);
        }
        return task;
    }

    public boolean contains(String taskId) {
        return get(taskId) != null;
    }

    public List<Task> getByGroup(String group) {
        Set<String> taskIds = groups.getOrDefault(group, Set.of());
        List<Task> result = new ArrayList<>();
        for (String taskId : taskIds) {
            Task task = get(taskId);
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    public List<Task> getByParent(String parentId) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (parentId.equals(task.getParentTaskId())) {
                result.add(task);
            }
        }
        return result;
    }

    public List<Task> getByStatus(TaskStatus status) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
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
        List<Task> result = new ArrayList<>();
        for (String taskId : new ArrayList<>(tasks.keySet())) {
            Task task = get(taskId);
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    public Set<String> keys() {
        cleanupStale();
        return new LinkedHashSet<>(tasks.keySet());
    }

    public Collection<Task> values() {
        return List.copyOf(getAll());
    }

    public Set<Map.Entry<String, Task>> items() {
        Map<String, Task> liveItems = new ConcurrentHashMap<>();
        for (String taskId : new ArrayList<>(tasks.keySet())) {
            Task task = get(taskId);
            if (task != null) {
                liveItems.put(taskId, task);
            }
        }
        return Set.copyOf(liveItems.entrySet());
    }

    public Task pop(String taskId) {
        return pop(taskId, null);
    }

    public Task pop(String taskId, Task defaultValue) {
        Task task = removeUnsafe(taskId);
        return task == null ? defaultValue : task;
    }

    public Task removeUnsafe(String taskId) {
        WeakReference<Task> reference = tasks.remove(taskId);
        Task task = reference == null ? null : reference.get();
        if (task == null) {
            removeTaskIdFromGroups(taskId);
            return task;
        }
        removeTaskIdFromGroup(taskId, task.getGroup());
        return task;
    }

    public void removeUnsafeDeprecated(String taskId) {
        removeUnsafe(taskId);
    }

    private void cleanupStale() {
        for (String taskId : new ArrayList<>(tasks.keySet())) {
            get(taskId);
        }
    }

    private void removeTaskIdFromGroups(String taskId) {
        for (String group : new ArrayList<>(groups.keySet())) {
            removeTaskIdFromGroup(taskId, group);
        }
    }

    private void removeTaskIdFromGroup(String taskId, String group) {
        if (group == null) {
            return;
        }
        Set<String> groupTasks = groups.get(group);
        if (groupTasks != null) {
            groupTasks.remove(taskId);
            if (groupTasks.isEmpty()) {
                groups.remove(group);
            }
        }
    }

    public Task remove(String taskId) {
        return removeUnsafe(taskId);
    }

    public List<String> getGroupTaskIds(String group) {
        List<String> liveTaskIds = new ArrayList<>();
        for (String taskId : groups.getOrDefault(group, Set.of())) {
            if (contains(taskId)) {
                liveTaskIds.add(taskId);
            }
        }
        return liveTaskIds;
    }
}
