/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task manager responsible for task CRUD, status management,
 * priority management, and hierarchical relationship management.
 * <p>
 * Provides efficient index structures for fast task queries.
 * <p>
 * Mirrors Python's {@code TaskManager} in
 * {@code openjiuwen/core/controller/modules/task_manager.py}.
 */
public class TaskManager {

    private ControllerConfig config;
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<Integer, List<String>> priorityIndex = new HashMap<>();
    private final Map<String, Set<String>> parentToChildren = new HashMap<>();
    private final Map<String, String> childToParent = new HashMap<>();
    private final Set<String> rootTasks = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Runnable onTaskSubmitted;

    public TaskManager(ControllerConfig config) {
        this.config = config;
    }

    public ControllerConfig getConfig() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    /**
     * Register callback for task-submitted events.
     *
     * <p>Mirrors Python's {@code TaskManager.set_on_task_submitted} in
     * {@code openjiuwen/core/controller/modules/task_manager.py}.</p>
     *
     * @param callback callback to invoke when a task enters SUBMITTED, or null to clear
     */
    public void setOnTaskSubmitted(Runnable callback) {
        this.onTaskSubmitted = callback;
    }

    /**
     * Get task manager state for serialization.
     */
    public TaskManagerState getState() {
        lock.lock();
        try {
            Map<String, Task> tasksCopy = new HashMap<>();
            for (var entry : tasks.entrySet()) {
                tasksCopy.put(entry.getKey(), entry.getValue().copy());
            }
            Map<Integer, List<String>> priorityCopy = new HashMap<>();
            for (var entry : priorityIndex.entrySet()) {
                priorityCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            Map<String, Set<String>> parentChildCopy = new HashMap<>();
            for (var entry : parentToChildren.entrySet()) {
                parentChildCopy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return new TaskManagerState(
                    tasksCopy,
                    priorityCopy,
                    parentChildCopy,
                    new HashMap<>(childToParent),
                    new HashSet<>(rootTasks)
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * Load task manager state.
     */
    public void loadState(TaskManagerState state) {
        lock.lock();
        try {
            tasks.clear();
            tasks.putAll(state.getTasks());
            priorityIndex.clear();
            for (var entry : state.getPriorityIndex().entrySet()) {
                priorityIndex.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            parentToChildren.clear();
            for (var entry : state.getParentToChildren().entrySet()) {
                parentToChildren.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            childToParent.clear();
            childToParent.putAll(state.getChildrenToParent());
            rootTasks.clear();
            rootTasks.addAll(state.getRootTasks());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clear all task manager state.
     */
    public void clearState() {
        lock.lock();
        try {
            tasks.clear();
            priorityIndex.clear();
            parentToChildren.clear();
            childToParent.clear();
            rootTasks.clear();
        } finally {
            lock.unlock();
        }
    }

    // ==================== Task CRUD Operations ====================

    /**
     * Add task(s) to task queue.
     */
    public void addTask(Task task) {
        addTask(List.of(task));
    }

    /**
     * Add tasks to task queue.
     */
    public void addTask(List<Task> taskList) {
        lock.lock();
        try {
            for (Task t : taskList) {
                if (tasks.containsKey(t.getTaskId())) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                            "error_msg", t.getTaskId() + " already exists!");
                }
                tasks.put(t.getTaskId(), t.copy());

                // Update priority index
                priorityIndex.computeIfAbsent(t.getPriority(), k -> new ArrayList<>())
                        .add(t.getTaskId());

                // Update hierarchical relationships
                if (t.getParentTaskId() != null) {
                    parentToChildren.computeIfAbsent(t.getParentTaskId(), k -> new HashSet<>())
                            .add(t.getTaskId());
                    childToParent.put(t.getTaskId(), t.getParentTaskId());
                    rootTasks.remove(t.getTaskId());
                } else {
                    rootTasks.add(t.getTaskId());
                }
            }
        } finally {
            lock.unlock();
        }
        notifyIfSubmitted(taskList);
    }

    /**
     * Query tasks based on filter.
     *
     * @param taskFilter filter criteria, null returns all tasks
     * @return list of matching tasks (deep copies)
     */
    public List<Task> getTask(TaskFilter taskFilter) {
        lock.lock();
        try {
            if (taskFilter == null) {
                return new ArrayList<>(tasks.values());
            }

            Set<String> candidateIds = new HashSet<>();
            boolean hasPrimaryFilter = false;

            // Query by task_id
            if (taskFilter.getTaskId() != null) {
                hasPrimaryFilter = true;
                candidateIds.addAll(taskFilter.getTaskIdList());
            }

            // Query by session_id
            if (taskFilter.getSessionId() != null) {
                hasPrimaryFilter = true;
                for (Task t : tasks.values()) {
                    if (taskFilter.getSessionId().equals(t.getSessionId())) {
                        candidateIds.add(t.getTaskId());
                    }
                }
            }

            // Query by priority
            if (taskFilter.getPriority() != null) {
                hasPrimaryFilter = true;
                if (taskFilter.isHighestPriority()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                            "error_msg", "Priority 'highest' is not supported in getTask, use popTask instead");
                }
                Integer p = taskFilter.getPriorityAsInt();
                if (p != null && priorityIndex.containsKey(p)) {
                    candidateIds.addAll(priorityIndex.get(p));
                }
            }

            // Query by is_root
            if (taskFilter.isRoot()) {
                hasPrimaryFilter = true;
                candidateIds.addAll(rootTasks);
            }

            // If no primary filters, check all tasks if status/userId filters exist
            if (!hasPrimaryFilter && (taskFilter.getStatus() != null || taskFilter.getUserId() != null)) {
                candidateIds.addAll(tasks.keySet());
            }

            List<Task> result = filterCandidates(candidateIds, taskFilter);

            // Handle with_children
            if (taskFilter.isWithChildren()) {
                Set<String> childrenIds = new HashSet<>();
                for (Task task : result) {
                    collectAllChildren(task.getTaskId(), childrenIds);
                }
                for (String cid : childrenIds) {
                    if (tasks.containsKey(cid)) {
                        result.add(tasks.get(cid));
                    }
                }
            }

            return copyTasks(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pop tasks (query and remove).
     */
    public List<Task> popTask(TaskFilter taskFilter) {
        if (taskFilter == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                    "error_msg", "taskFilter cannot be null in popTask");
        }

        lock.lock();
        try {
            // Handle highest priority
            if (taskFilter.isHighestPriority()) {
                if (priorityIndex.isEmpty()) {
                    return List.of();
                }
                int maxPriority = priorityIndex.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                taskFilter = taskFilter.withPriority(maxPriority);
            }

            // Get matching tasks
            List<Task> result = getTaskInternal(taskFilter);

            // Collect all task IDs to remove
            Set<String> removeIds = new HashSet<>();
            for (Task t : result) {
                removeIds.add(t.getTaskId());
            }

            List<Task> toReturn = copyTasks(result);

            // Remove tasks
            for (String tid : removeIds) {
                removeTaskInternal(tid, removeIds);
            }

            return toReturn;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Update task(s).
     */
    public boolean updateTask(Task task) {
        return updateTask(List.of(task));
    }

    /**
     * Update tasks.
     */
    public boolean updateTask(List<Task> taskList) {
        lock.lock();
        try {
            boolean allSuccess = true;
            for (Task t : taskList) {
                if (!tasks.containsKey(t.getTaskId())) {
                    allSuccess = false;
                    continue;
                }
                Task oldTask = tasks.get(t.getTaskId());

                tasks.put(t.getTaskId(), t);

                // Update priority index
                if (oldTask.getPriority() != t.getPriority()) {
                    List<String> oldList = priorityIndex.get(oldTask.getPriority());
                    if (oldList != null) {
                        oldList.remove(t.getTaskId());
                    }
                }
                priorityIndex.computeIfAbsent(t.getPriority(), k -> new ArrayList<>());
                if (!priorityIndex.get(t.getPriority()).contains(t.getTaskId())) {
                    priorityIndex.get(t.getPriority()).add(t.getTaskId());
                }

                // Update hierarchical relationships if parent changed
                String oldParent = oldTask.getParentTaskId();
                String newParent = t.getParentTaskId();
                if (!java.util.Objects.equals(oldParent, newParent)) {
                    // Remove old relationship
                    if (oldParent != null) {
                        Set<String> children = parentToChildren.get(oldParent);
                        if (children != null) {
                            children.remove(t.getTaskId());
                        }
                        childToParent.remove(t.getTaskId());
                    }

                    // Add new relationship
                    if (newParent != null) {
                        parentToChildren.computeIfAbsent(newParent, k -> new HashSet<>())
                                .add(t.getTaskId());
                        childToParent.put(t.getTaskId(), newParent);
                        rootTasks.remove(t.getTaskId());
                    } else {
                        rootTasks.add(t.getTaskId());
                        childToParent.remove(t.getTaskId());
                    }
                }
            }
            return allSuccess;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove tasks based on filter.
     */
    public void removeTask(TaskFilter taskFilter) {
        if (taskFilter == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                    "error_msg", "taskFilter cannot be null in removeTask");
        }
        if (taskFilter.isHighestPriority()) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                    "error_msg", "Priority 'highest' is not supported in removeTask");
        }

        lock.lock();
        try {
            List<Task> tasksToRemove = getTaskInternal(taskFilter);
            Set<String> removeIds = new HashSet<>();
            for (Task t : tasksToRemove) {
                removeIds.add(t.getTaskId());
            }
            for (String tid : removeIds) {
                removeTaskInternal(tid, removeIds);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get child tasks for a single task ID.
     */
    public List<Task> getChildTask(String taskId, boolean isRecursive) {
        return getChildTask(List.of(taskId), isRecursive);
    }

    /**
     * Get child tasks for multiple task IDs.
     * Mirrors Python's support for {@code task_id: Union[str, List[str]]}.
     */
    public List<Task> getChildTask(List<String> taskIds, boolean isRecursive) {
        lock.lock();
        try {
            Set<String> childrenIds = new HashSet<>();
            for (String tid : taskIds) {
                if (parentToChildren.containsKey(tid)) {
                    if (isRecursive) {
                        collectAllChildren(tid, childrenIds);
                    } else {
                        childrenIds.addAll(parentToChildren.get(tid));
                    }
                }
            }
            List<Task> result = new ArrayList<>();
            for (String cid : childrenIds) {
                if (tasks.containsKey(cid)) {
                    result.add(tasks.get(cid).copy());
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    // ==================== Task Status Management ====================

    /**
     * Update task status.
     */
    public void updateTaskStatus(String taskId, TaskStatus newStatus) {
        updateTaskStatus(List.of(taskId), newStatus, false, false, null);
    }

    /**
     * Update task status with error message.
     */
    public void updateTaskStatus(String taskId, TaskStatus newStatus, String errorMessage) {
        updateTaskStatus(List.of(taskId), newStatus, false, false, errorMessage);
    }

    /**
     * Update task status for a list of task IDs.
     * Mirrors Python's support for {@code task_id: Union[str, List[str]]}.
     */
    public void updateTaskStatus(List<String> taskIds, TaskStatus newStatus,
                                 boolean withChildren, boolean isRecursive, String errorMessage) {
        lock.lock();
        try {
            Set<String> allTaskIds = new HashSet<>(taskIds);

            if (withChildren) {
                for (String tid : taskIds) {
                    if (isRecursive) {
                        collectAllChildren(tid, allTaskIds);
                    } else if (parentToChildren.containsKey(tid)) {
                        allTaskIds.addAll(parentToChildren.get(tid));
                    }
                }
            }

            for (String tid : allTaskIds) {
                Task task = tasks.get(tid);
                if (task != null) {
                    task.setStatus(newStatus);
                    if (newStatus == TaskStatus.FAILED) {
                        task.setErrorMessage(errorMessage != null ? errorMessage : "Task execution failed");
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        if (newStatus == TaskStatus.SUBMITTED && onTaskSubmitted != null) {
            onTaskSubmitted.run();
        }
    }

    // ==================== Task Priority Management ====================

    /**
     * Set task priority for a single task ID.
     */
    public void setPriority(String taskId, int newPriority,
                            boolean withChildren, boolean isRecursive) {
        setPriority(List.of(taskId), newPriority, withChildren, isRecursive);
    }

    /**
     * Set task priority for a list of task IDs.
     * Mirrors Python's support for {@code task_id: Union[str, List[str]]}.
     */
    public void setPriority(List<String> taskIds, int newPriority,
                            boolean withChildren, boolean isRecursive) {
        lock.lock();
        try {
            Set<String> allTaskIds = new HashSet<>(taskIds);

            if (withChildren) {
                for (String tid : taskIds) {
                    if (isRecursive) {
                        collectAllChildren(tid, allTaskIds);
                    } else if (parentToChildren.containsKey(tid)) {
                        allTaskIds.addAll(parentToChildren.get(tid));
                    }
                }
            }

            for (String tid : allTaskIds) {
                Task task = tasks.get(tid);
                if (task != null) {
                    int oldPriority = task.getPriority();
                    task.setPriority(newPriority);

                    if (oldPriority != newPriority) {
                        List<String> oldList = priorityIndex.get(oldPriority);
                        if (oldList != null) {
                            oldList.remove(tid);
                        }
                        priorityIndex.computeIfAbsent(newPriority, k -> new ArrayList<>()).add(tid);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // ==================== Internal helpers ====================

    private void collectAllChildren(String parentId, Set<String> childrenSet) {
        Set<String> children = parentToChildren.get(parentId);
        if (children != null) {
            for (String childId : children) {
                childrenSet.add(childId);
                collectAllChildren(childId, childrenSet);
            }
        }
    }

    private void notifyIfSubmitted(List<Task> taskList) {
        Runnable callback = onTaskSubmitted;
        if (callback == null) {
            return;
        }
        for (Task task : taskList) {
            if (task.getStatus() == TaskStatus.SUBMITTED) {
                callback.run();
                return;
            }
        }
    }

    /**
     * Internal getTask (no lock, caller holds lock).
     */
    private List<Task> getTaskInternal(TaskFilter taskFilter) {
        if (taskFilter == null) {
            return new ArrayList<>(tasks.values());
        }

        Set<String> candidateIds = new HashSet<>();
        boolean hasPrimaryFilter = false;

        if (taskFilter.getTaskId() != null) {
            hasPrimaryFilter = true;
            candidateIds.addAll(taskFilter.getTaskIdList());
        }
        if (taskFilter.getSessionId() != null) {
            hasPrimaryFilter = true;
            for (Task t : tasks.values()) {
                if (taskFilter.getSessionId().equals(t.getSessionId())) {
                    candidateIds.add(t.getTaskId());
                }
            }
        }
        if (taskFilter.getPriority() != null && !taskFilter.isHighestPriority()) {
            hasPrimaryFilter = true;
            Integer p = taskFilter.getPriorityAsInt();
            if (p != null && priorityIndex.containsKey(p)) {
                candidateIds.addAll(priorityIndex.get(p));
            }
        }
        if (taskFilter.isRoot()) {
            hasPrimaryFilter = true;
            candidateIds.addAll(rootTasks);
        }
        if (!hasPrimaryFilter && (taskFilter.getStatus() != null || taskFilter.getUserId() != null)) {
            candidateIds.addAll(tasks.keySet());
        }

        List<Task> result = filterCandidates(candidateIds, taskFilter);

        if (taskFilter.isWithChildren()) {
            Set<String> childrenIds = new HashSet<>();
            for (Task task : result) {
                collectAllChildren(task.getTaskId(), childrenIds);
            }
            for (String cid : childrenIds) {
                if (tasks.containsKey(cid)) {
                    result.add(tasks.get(cid));
                }
            }
        }

        return result;
    }

    private List<Task> filterCandidates(Set<String> candidateIds, TaskFilter filter) {
        List<Task> result = new ArrayList<>();
        for (String tid : candidateIds) {
            Task task = tasks.get(tid);
            if (task == null) {
                continue;
            }
            if (filter.getStatus() != null && task.getStatus() != filter.getStatus()) {
                continue;
            }
            if (filter.getUserId() != null) {
                Map<String, Object> meta = task.getMetadata();
                if (meta == null || !filter.getUserId().equals(meta.get("user_id"))) {
                    continue;
                }
            }
            result.add(task);
        }
        return result;
    }

    private void removeTaskInternal(String tid, Set<String> allRemoveIds) {
        Task task = tasks.get(tid);
        if (task == null) {
            return;
        }

        // Remove from priority index
        List<String> priorityList = priorityIndex.get(task.getPriority());
        if (priorityList != null) {
            priorityList.remove(tid);
        }

        // Remove from hierarchical relationships
        if (task.getParentTaskId() != null) {
            Set<String> siblings = parentToChildren.get(task.getParentTaskId());
            if (siblings != null) {
                siblings.remove(tid);
            }
            childToParent.remove(tid);
        } else {
            rootTasks.remove(tid);
        }

        // Promote children to root if parent is being removed
        Set<String> children = parentToChildren.get(tid);
        if (children != null) {
            for (String childId : new HashSet<>(children)) {
                if (!allRemoveIds.contains(childId) && tasks.containsKey(childId)) {
                    Task childTask = tasks.get(childId);
                    childTask.setParentTaskId(null);
                    rootTasks.add(childId);
                    childToParent.remove(childId);
                }
            }
            parentToChildren.remove(tid);
        }

        tasks.remove(tid);
    }

    private List<Task> copyTasks(List<Task> taskList) {
        List<Task> copies = new ArrayList<>(taskList.size());
        for (Task t : taskList) {
            copies.add(t.copy());
        }
        return copies;
    }
}
