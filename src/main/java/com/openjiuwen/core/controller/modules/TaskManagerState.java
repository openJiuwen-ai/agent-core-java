// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task Manager State.
 *
 * <p>Used for serialization and restoration of task manager state.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskManagerState {

    private final Map<String, Task> tasks;
    private final Map<Integer, List<String>> priorityIndex;
    private final Map<String, Set<String>> parentToChildren;
    private final Map<String, String> childrenToParent;
    private final Set<String> rootTasks;

    /**
     * Constructor.
     *
     * @param tasks            the task map
     * @param priorityIndex    the priority index
     * @param parentToChildren the parent-to-children index
     * @param childrenToParent the children-to-parent index
     * @param rootTasks        the root task set
     */
    public TaskManagerState(Map<String, Task> tasks,
                             Map<Integer, List<String>> priorityIndex,
                             Map<String, Set<String>> parentToChildren,
                             Map<String, String> childrenToParent,
                             Set<String> rootTasks) {
        this.tasks = new HashMap<>(tasks);
        this.priorityIndex = new HashMap<>(priorityIndex);
        this.parentToChildren = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : parentToChildren.entrySet()) {
            this.parentToChildren.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        this.childrenToParent = new HashMap<>(childrenToParent);
        this.rootTasks = new HashSet<>(rootTasks);
    }

    public Map<String, Task> getTasks() {
        return tasks;
    }

    public Map<Integer, List<String>> getPriorityIndex() {
        return priorityIndex;
    }

    public Map<String, Set<String>> getParentToChildren() {
        return parentToChildren;
    }

    public Map<String, String> getChildrenToParent() {
        return childrenToParent;
    }

    public Set<String> getRootTasks() {
        return rootTasks;
    }
}

