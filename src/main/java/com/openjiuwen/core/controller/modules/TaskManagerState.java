/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task manager state for serialization and restoration.
 * <p>
 * Mirrors Python's {@code TaskManagerState(BaseModel)}.
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
    @SuppressWarnings("unchecked")
    public static TaskManagerState fromMap(Map<String, Object> map) {
        TaskManagerState state = new TaskManagerState();

        Map<String, Object> tasksRaw = (Map<String, Object>) map.get("tasks");
        if (tasksRaw != null) {
            Map<String, Task> tasks = new HashMap<>();
            for (var entry : tasksRaw.entrySet()) {
                tasks.put(entry.getKey(), Task.fromMap((Map<String, Object>) entry.getValue()));
            }
            state.setTasks(tasks);
        }

        Map<String, Object> priorityRaw = (Map<String, Object>) map.get("priority_index");
        if (priorityRaw != null) {
            Map<Integer, List<String>> pi = new HashMap<>();
            for (var entry : priorityRaw.entrySet()) {
                pi.put(Integer.parseInt(entry.getKey().toString()), (List<String>) entry.getValue());
            }
            state.setPriorityIndex(pi);
        }

        Map<String, Object> p2cRaw = (Map<String, Object>) map.get("parent_to_children");
        if (p2cRaw != null) {
            Map<String, Set<String>> p2c = new HashMap<>();
            for (var entry : p2cRaw.entrySet()) {
                p2c.put(entry.getKey(), new HashSet<>((List<String>) entry.getValue()));
            }
            state.setParentToChildren(p2c);
        }

        Map<String, String> c2p = (Map<String, String>) map.get("children_to_parent");
        if (c2p != null) {
            state.setChildrenToParent(new HashMap<>(c2p));
        }

        List<String> rootRaw = (List<String>) map.get("root_tasks");
        if (rootRaw != null) {
            state.setRootTasks(new HashSet<>(rootRaw));
        }

        return state;
    }
}
