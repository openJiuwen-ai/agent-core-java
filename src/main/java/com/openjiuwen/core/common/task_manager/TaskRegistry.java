/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for common task-manager tasks with weak-reference storage.
 * 
 * @since 0.1.7
 */
public class TaskRegistry {
    private final Map<String, WeakReference<Task>> tasks = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Set<String>> groups = new LinkedHashMap<>();

    /**
     * add.
     * 
     * @param task task
     * @since 0.1.7
     */
    public void add(Task task) {
        tasks.put(task.getTaskId(), new WeakReference<>(task));
        if (task.getGroup() != null && !task.getGroup().isBlank()) {
            groups.computeIfAbsent(task.getGroup(), ignored -> new LinkedHashSet<>()).add(task.getTaskId());
        }
    }

    /**
     * get.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public Task get(String taskId) {
        WeakReference<Task> ref = tasks.get(taskId);
        Task task = ref != null ? ref.get() : null;
        if (task == null && ref != null) {
            removeUnsafe(taskId);
        }
        return task;
    }

    /**
     * contains.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public boolean contains(String taskId) {
        return get(taskId) != null;
    }

    /**
     * getByGroup.
     * 
     * @param group group
     * @return the result
     * @since 0.1.7
     */
    public List<Task> getByGroup(String group) {
        Set<String> ids = groups.getOrDefault(group, Set.of());
        List<Task> result = new ArrayList<>();
        for (String id : ids) {
            Task task = get(id);
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * getByParent.
     * 
     * @param parentId parentId
     * @return the result
     * @since 0.1.7
     */
    public List<Task> getByParent(String parentId) {
        List<Task> result = new ArrayList<>();
        for (WeakReference<Task> ref : tasks.values()) {
            Task task = ref.get();
            if (task == null) {
                continue;
            }
            if (parentId != null && parentId.equals(task.getParentTaskId())) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * getByStatus.
     * 
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public List<Task> getByStatus(TaskStatus status) {
        List<Task> result = new ArrayList<>();
        for (WeakReference<Task> ref : tasks.values()) {
            Task task = ref.get();
            if (task == null) {
                continue;
            }
            if (task.getStatus() == status) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * getRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Task> getRunning() {
        return getByStatus(TaskStatus.RUNNING);
    }

    /**
     * getAll.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Task> getAll() {
        List<Task> result = new ArrayList<>();
        for (WeakReference<Task> ref : tasks.values()) {
            Task task = ref.get();
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * keys.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Collection<String> keys() {
        cleanupStaleReferences();
        return new ArrayList<>(tasks.keySet()); // LinkedHashMap preserves insertion order
    }

    /**
     * items.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<Map.Entry<String, Task>> items() {
        cleanupStaleReferences();
        Set<Map.Entry<String, Task>> result = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, WeakReference<Task>> entry : tasks.entrySet()) {
            Task task = entry.getValue().get();
            if (task != null) {
                result.add(Map.entry(entry.getKey(), task));
            }
        }
        return result;
    }

    /**
     * removeUnsafe.
     * 
     * @param taskId taskId
     * @since 0.1.7
     */
    public void removeUnsafe(String taskId) {
        WeakReference<Task> ref = tasks.remove(taskId);
        Task task = ref != null ? ref.get() : null;
        if (task == null) {
            groups.values().forEach(ids -> ids.remove(taskId));
            groups.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            return;
        }
        if (task.getGroup() != null && !task.getGroup().isBlank()) {
            Set<String> ids = groups.get(task.getGroup());
            if (ids != null) {
                ids.remove(taskId);
                if (ids.isEmpty()) {
                    groups.remove(task.getGroup());
                }
            }
        }
    }

    /**
     * getGroupTaskIds.
     * 
     * @param group group
     * @return the result
     * @since 0.1.7
     */
    public List<String> getGroupTaskIds(String group) {
        return new ArrayList<>(groups.getOrDefault(group, Set.of()));
    }

    /**
     * clear.
     * 
     * @since 0.1.7
     */
    public void clear() {
        tasks.clear();
        groups.clear();
    }

    /**
     * cleanupStaleReferences.
     * 
     * @since 0.1.7
     */
    private void cleanupStaleReferences() {
        List<String> staleIds = new ArrayList<>();
        for (Map.Entry<String, WeakReference<Task>> entry : tasks.entrySet()) {
            if (entry.getValue().get() == null) {
                staleIds.add(entry.getKey());
            }
        }
        for (String staleId : staleIds) {
            removeUnsafe(staleId);
        }
    }
}
