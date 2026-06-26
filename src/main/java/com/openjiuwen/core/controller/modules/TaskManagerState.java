/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task manager state for serialization and restoration.
 * <p>
 * Mirrors Python's {@code TaskManagerState(BaseModel)} in
 * {@code openjiuwen/core/controller/modules/task_manager.py}.
 */
public class TaskManagerState {

    private Map<String, Task> tasks;
    private Map<Integer, List<String>> priorityIndex;
    private Map<String, Set<String>> parentToChildren;
    private Map<String, String> childrenToParent;
    private Set<String> rootTasks;

    public TaskManagerState() {
        this.tasks = new HashMap<>();
        this.priorityIndex = new HashMap<>();
        this.parentToChildren = new HashMap<>();
        this.childrenToParent = new HashMap<>();
        this.rootTasks = new HashSet<>();
    }

    public TaskManagerState(Map<String, Task> tasks,
                            Map<Integer, List<String>> priorityIndex,
                            Map<String, Set<String>> parentToChildren,
                            Map<String, String> childrenToParent,
                            Set<String> rootTasks) {
        this.tasks = tasks;
        this.priorityIndex = priorityIndex;
        this.parentToChildren = parentToChildren;
        this.childrenToParent = childrenToParent;
        this.rootTasks = rootTasks;
    }

    public Map<String, Task> getTasks() {
        return tasks;
    }

    public void setTasks(Map<String, Task> tasks) {
        this.tasks = tasks;
    }

    public Map<Integer, List<String>> getPriorityIndex() {
        return priorityIndex;
    }

    public void setPriorityIndex(Map<Integer, List<String>> priorityIndex) {
        this.priorityIndex = priorityIndex;
    }

    public Map<String, Set<String>> getParentToChildren() {
        return parentToChildren;
    }

    public void setParentToChildren(Map<String, Set<String>> parentToChildren) {
        this.parentToChildren = parentToChildren;
    }

    public Map<String, String> getChildrenToParent() {
        return childrenToParent;
    }

    public void setChildrenToParent(Map<String, String> childrenToParent) {
        this.childrenToParent = childrenToParent;
    }

    public Set<String> getRootTasks() {
        return rootTasks;
    }

    public void setRootTasks(Set<String> rootTasks) {
        this.rootTasks = rootTasks;
    }

    /**
     * Serialize state to a plain map for persistence.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> tasksMap = new LinkedHashMap<>();
        for (var entry : tasks.entrySet()) {
            tasksMap.put(entry.getKey(), entry.getValue().toMap());
        }
        map.put("tasks", tasksMap);
        map.put("priority_index", priorityIndex);
        map.put("parent_to_children", parentToChildren);
        map.put("children_to_parent", childrenToParent);
        map.put("root_tasks", new ArrayList<>(rootTasks));
        return map;
    }

    /**
     * Deserialize state from a plain map.
     */
    public static TaskManagerState fromMap(Map<String, Object> map) {
        TaskManagerState state = new TaskManagerState();

        Object tasksRaw = map.get("tasks");
        if (tasksRaw instanceof Map<?, ?> tasksMapRaw) {
            Map<String, Task> tasks = new HashMap<>();
            for (var entry : tasksMapRaw.entrySet()) {
                String taskId = String.valueOf(entry.getKey());
                Object rawTask = entry.getValue();
                if (rawTask instanceof Task task) {
                    tasks.put(taskId, task.copy());
                } else if (rawTask instanceof Map<?, ?> taskMap) {
                    tasks.put(taskId, Task.fromMap(toStringObjectMap(taskMap)));
                } else {
                    throw new IllegalArgumentException("Task state for " + taskId + " must be a task or map");
                }
            }
            state.setTasks(tasks);
        }

        Object priorityRaw = map.get("priority_index");
        if (priorityRaw instanceof Map<?, ?> priorityMapRaw) {
            Map<Integer, List<String>> pi = new HashMap<>();
            for (var entry : priorityMapRaw.entrySet()) {
                pi.put(Integer.parseInt(String.valueOf(entry.getKey())), toStringList(entry.getValue()));
            }
            state.setPriorityIndex(pi);
        }

        Object p2cRaw = map.get("parent_to_children");
        if (p2cRaw instanceof Map<?, ?> p2cMapRaw) {
            Map<String, Set<String>> p2c = new HashMap<>();
            for (var entry : p2cMapRaw.entrySet()) {
                p2c.put(String.valueOf(entry.getKey()), new HashSet<>(toStringList(entry.getValue())));
            }
            state.setParentToChildren(p2c);
        }

        Object c2pRaw = map.get("children_to_parent");
        if (c2pRaw instanceof Map<?, ?> c2pMapRaw) {
            Map<String, String> c2p = new HashMap<>();
            for (var entry : c2pMapRaw.entrySet()) {
                c2p.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            state.setChildrenToParent(c2p);
        }

        Object rootRaw = map.get("root_tasks");
        if (rootRaw != null) {
            state.setRootTasks(new HashSet<>(toStringList(rootRaw)));
        }

        return state;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : rawMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<String> toStringList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("Expected collection but got " + value.getClass().getName());
        }
        List<String> result = new ArrayList<>(collection.size());
        for (Object item : collection) {
            result.add(String.valueOf(item));
        }
        return result;
    }
}
