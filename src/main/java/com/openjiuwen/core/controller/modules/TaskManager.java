// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task Manager.
 *
 * <p>Responsible for task CRUD operations, status management, priority management,
 * and hierarchical relationship management.
 * Provides efficient index structures for fast task queries.
 *
 * <p>Index Structure:
 * <ul>
 *   <li>priorityIndex: Priority index for fast task lookup by priority</li>
 *   <li>parentToChildren: Parent-to-children relationship index</li>
 *   <li>childToParent: Child-to-parent relationship index</li>
 *   <li>rootTasks: Root task set for fast root task lookup</li>
 * </ul>
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskManager {

    private ControllerConfig config;
    private final Map<String, Task> tasks = new LinkedHashMap<>();

    // Priority index: priority -> List[task_id]
    private final Map<Integer, List<String>> priorityIndex = new HashMap<>();

    // Parent-to-children: parent_task_id -> Set[child_task_id]
    private final Map<String, Set<String>> parentToChildren = new HashMap<>();

    // Child-to-parent: child_task_id -> parent_task_id
    private final Map<String, String> childToParent = new HashMap<>();

    // Root task set
    private final Set<String> rootTasks = new LinkedHashSet<>();

    // Lock for thread safety
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Constructor.
     *
     * @param config the controller configuration
     */
    public TaskManager(ControllerConfig config) {
        this.config = config;
    }

    // ==================== Property access ====================

    public ControllerConfig getConfig() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    /**
     * Direct access to internal tasks map (for test inspection).
     */
    public Map<String, Task> getTasks() {
        return tasks;
    }

    public Map<Integer, List<String>> getPriorityIndex() {
        return priorityIndex;
    }

    public Map<String, Set<String>> getParentToChildren() {
        return parentToChildren;
    }

    public Map<String, String> getChildToParent() {
        return childToParent;
    }

    public Set<String> getRootTasks() {
        return rootTasks;
    }

    // ==================== State Management ====================

    /**
     * Gets the task manager state.
     *
     * @return a copy of the current state
     */
    public TaskManagerState getState() {
        lock.lock();
        try {
            return new TaskManagerState(
                new LinkedHashMap<>(tasks),
                deepCopyPriorityIndex(),
                deepCopyParentToChildren(),
                new HashMap<>(childToParent),
                new LinkedHashSet<>(rootTasks)
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads the task manager state.
     *
     * @param state the state to load
     */
    public void loadState(TaskManagerState state) {
        lock.lock();
        try {
            tasks.clear();
            tasks.putAll(state.getTasks());

            priorityIndex.clear();
            for (Map.Entry<Integer, List<String>> entry : state.getPriorityIndex().entrySet()) {
                priorityIndex.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            parentToChildren.clear();
            for (Map.Entry<String, Set<String>> entry : state.getParentToChildren().entrySet()) {
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
     * Clears all task manager state.
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
     * Add a single task.
     *
     * @param task the task to add
     */
    public void addTask(Task task) {
        addTask(List.of(task));
    }

    /**
     * Add multiple tasks.
     *
     * @param taskList the tasks to add
     */
    public void addTask(List<Task> taskList) {
        lock.lock();
        try {
            for (Task t : taskList) {
                if (tasks.containsKey(t.getTaskId())) {
                    throw ErrorBuilder.build(
                        StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                        null, null, null,
                        Map.of("error_msg", t.getTaskId() + " already exists!")
                    );
                }

                // Deep copy task (clone by rebuilding)
                tasks.put(t.getTaskId(), copyTask(t));

                // Update priority index
                priorityIndex.computeIfAbsent(t.getPriority(), k -> new ArrayList<>()).add(t.getTaskId());

                // Update hierarchical relationship index
                if (t.getParentTaskId() != null) {
                    parentToChildren.computeIfAbsent(t.getParentTaskId(), k -> new HashSet<>()).add(t.getTaskId());
                    childToParent.put(t.getTaskId(), t.getParentTaskId());
                    rootTasks.remove(t.getTaskId());
                } else {
                    rootTasks.add(t.getTaskId());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Query tasks.
     *
     * @param taskFilter the filter criteria (null returns all tasks)
     * @return list of matching tasks
     */
    public List<Task> getTask(TaskFilter taskFilter) {
        lock.lock();
        try {
            if (taskFilter == null) {
                return new ArrayList<>(tasks.values());
            }

            // Check for "highest" priority in get_task
            if (taskFilter.isPriorityHighest()) {
                throw ErrorBuilder.build(
                    StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                    null, null, null,
                    Map.of("error_msg", "Priority 'highest' is not supported in get_task, use pop_task instead")
                );
            }

            return filterTasks(taskFilter);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pop tasks (query and remove).
     *
     * @param taskFilter the filter criteria (cannot be null)
     * @return list of matching tasks (already removed)
     */
    public List<Task> popTask(TaskFilter taskFilter) {
        if (taskFilter == null) {
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                null, null, null,
                Map.of("error_msg", "task_filter cannot be None in pop_task")
            );
        }

        lock.lock();
        try {
            // Handle "highest" priority
            Integer resolvedPriority = taskFilter.getPriority();
            boolean useHighest = taskFilter.isPriorityHighest();
            if (useHighest) {
                if (priorityIndex.isEmpty()) {
                    return List.of();
                }
                resolvedPriority = Collections.max(priorityIndex.keySet());
            }

            // Create a resolved filter for actual querying
            List<Task> resultTasks = filterTasksWithResolvedPriority(taskFilter, resolvedPriority, useHighest);

            // Collect IDs to remove
            Set<String> taskIdsToRemove = new HashSet<>();
            for (Task t : resultTasks) {
                taskIdsToRemove.add(t.getTaskId());
            }

            // Remove tasks
            removeTasksInternal(taskIdsToRemove);

            return resultTasks;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Update a single task.
     *
     * @param task the task to update
     * @return true if updated, false if not found
     */
    public boolean updateTask(Task task) {
        return updateTask(List.of(task));
    }

    /**
     * Update multiple tasks.
     *
     * @param taskList the tasks to update
     * @return true if all updated, false if any not found
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

                // Update task
                tasks.put(t.getTaskId(), t);

                // Update priority index
                List<String> oldPriorityList = priorityIndex.get(oldTask.getPriority());
                if (oldPriorityList != null) {
                    oldPriorityList.remove(t.getTaskId());
                }
                List<String> newPriorityList = priorityIndex.get(t.getPriority());
                if (newPriorityList == null || !newPriorityList.contains(t.getTaskId())) {
                    priorityIndex.computeIfAbsent(t.getPriority(), k -> new ArrayList<>()).add(t.getTaskId());
                }

                // Update hierarchical relationship
                String oldParent = oldTask.getParentTaskId();
                String newParent = t.getParentTaskId();
                if (!Objects.equals(oldParent, newParent)) {
                    // Remove from old parent
                    if (oldParent != null) {
                        Set<String> oldChildren = parentToChildren.get(oldParent);
                        if (oldChildren != null) {
                            oldChildren.remove(t.getTaskId());
                        }
                        childToParent.remove(t.getTaskId());
                    }

                    // Add to new parent
                    if (newParent != null) {
                        parentToChildren.computeIfAbsent(newParent, k -> new HashSet<>()).add(t.getTaskId());
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
     * Remove tasks.
     *
     * @param taskFilter the filter criteria (cannot be null)
     */
    public void removeTask(TaskFilter taskFilter) {
        if (taskFilter == null) {
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                null, null, null,
                Map.of("error_msg", "task_filter cannot be None in remove_task")
            );
        }

        lock.lock();
        try {
            // Check for "highest" in remove_task
            if (taskFilter.isPriorityHighest()) {
                throw ErrorBuilder.build(
                    StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                    null, null, null,
                    Map.of("error_msg", "Priority 'highest' is not supported in remove_task")
                );
            }

            List<Task> tasksToRemove = filterTasks(taskFilter);
            Set<String> taskIdsToRemove = new HashSet<>();
            for (Task t : tasksToRemove) {
                taskIdsToRemove.add(t.getTaskId());
            }

            removeTasksInternal(taskIdsToRemove);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get child tasks.
     *
     * @param taskId      the parent task ID
     * @param isRecursive whether to recursively get all descendants
     * @return list of child tasks
     */
    public List<Task> getChildTask(String taskId, boolean isRecursive) {
        return getChildTask(List.of(taskId), isRecursive);
    }

    /**
     * Get child tasks for multiple parents.
     *
     * @param taskIds     the parent task IDs
     * @param isRecursive whether to recursively get all descendants
     * @return list of child tasks
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
                    result.add(tasks.get(cid));
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    // ==================== Status Management ====================

    /**
     * Update task status for a single task.
     *
     * @param taskId    the task ID
     * @param newStatus the new status
     */
    public void updateTaskStatus(String taskId, TaskStatus newStatus) {
        updateTaskStatus(List.of(taskId), newStatus, false, false);
    }

    /**
     * Update task status for multiple tasks.
     *
     * @param taskIds   the task IDs
     * @param newStatus the new status
     */
    public void updateTaskStatus(List<String> taskIds, TaskStatus newStatus) {
        updateTaskStatus(taskIds, newStatus, false, false);
    }

    /**
     * Update task status with child task support.
     *
     * @param taskId       the task ID
     * @param newStatus    the new status
     * @param withChildren whether to also update children
     * @param isRecursive  whether to recursively update
     */
    public void updateTaskStatus(String taskId, TaskStatus newStatus,
                                  boolean withChildren, boolean isRecursive) {
        updateTaskStatus(List.of(taskId), newStatus, withChildren, isRecursive);
    }

    /**
     * Update task status with full options.
     *
     * @param taskIds      the task IDs
     * @param newStatus    the new status
     * @param withChildren whether to also update children
     * @param isRecursive  whether to recursively update
     */
    public void updateTaskStatus(List<String> taskIds, TaskStatus newStatus,
                                  boolean withChildren, boolean isRecursive) {
        lock.lock();
        try {
            Set<String> allTaskIds = new LinkedHashSet<>(taskIds);

            if (withChildren) {
                for (String tid : taskIds) {
                    if (isRecursive) {
                        collectAllChildren(tid, allTaskIds);
                    } else {
                        if (parentToChildren.containsKey(tid)) {
                            allTaskIds.addAll(parentToChildren.get(tid));
                        }
                    }
                }
            }

            for (String tid : allTaskIds) {
                if (tasks.containsKey(tid)) {
                    tasks.get(tid).setStatus(newStatus);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // ==================== Priority Management ====================

    /**
     * Set task priority.
     *
     * @param taskId      the task ID
     * @param newPriority the new priority
     */
    public void setPriority(String taskId, int newPriority) {
        setPriority(List.of(taskId), newPriority, false, false);
    }

    /**
     * Set task priority (string version).
     *
     * @param taskId      the task ID
     * @param newPriority the new priority as string
     */
    public void setPriority(String taskId, String newPriority) {
        setPriority(List.of(taskId), Integer.parseInt(newPriority), false, false);
    }

    /**
     * Set task priority with child support.
     *
     * @param taskId       the task ID
     * @param newPriority  the new priority
     * @param withChildren whether to also update children
     * @param isRecursive  whether to recursively update
     */
    public void setPriority(String taskId, int newPriority,
                             boolean withChildren, boolean isRecursive) {
        setPriority(List.of(taskId), newPriority, withChildren, isRecursive);
    }

    /**
     * Set task priority with full options.
     *
     * @param taskIds      the task IDs
     * @param newPriority  the new priority
     * @param withChildren whether to also update children
     * @param isRecursive  whether to recursively update
     */
    public void setPriority(List<String> taskIds, int newPriority,
                             boolean withChildren, boolean isRecursive) {
        lock.lock();
        try {
            Set<String> allTaskIds = new LinkedHashSet<>(taskIds);

            if (withChildren) {
                for (String tid : taskIds) {
                    if (isRecursive) {
                        collectAllChildren(tid, allTaskIds);
                    } else {
                        if (parentToChildren.containsKey(tid)) {
                            allTaskIds.addAll(parentToChildren.get(tid));
                        }
                    }
                }
            }

            for (String tid : allTaskIds) {
                if (tasks.containsKey(tid)) {
                    Task task = tasks.get(tid);
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

    // ==================== Private Helper Methods ====================

    /**
     * Recursively collect all child task IDs.
     */
    private void collectAllChildren(String parentId, Set<String> childrenSet) {
        Set<String> children = parentToChildren.get(parentId);
        if (children != null) {
            for (String childId : children) {
                childrenSet.add(childId);
                collectAllChildren(childId, childrenSet);
            }
        }
    }

    /**
     * Internal method to filter tasks based on criteria.
     * Caller must hold the lock.
     */
    private List<Task> filterTasks(TaskFilter filter) {
        return filterTasksWithResolvedPriority(filter, filter.getPriority(), false);
    }

    /**
     * Internal method to filter tasks with optional resolved priority.
     * Caller must hold the lock.
     */
    private List<Task> filterTasksWithResolvedPriority(TaskFilter filter,
                                                        Integer resolvedPriority,
                                                        boolean wasHighest) {
        Set<String> candidateIds = new LinkedHashSet<>();

        // Query by task_id
        if (filter.getTaskId() != null) {
            candidateIds.add(filter.getTaskId());
        }
        if (filter.getTaskIds() != null) {
            candidateIds.addAll(filter.getTaskIds());
        }

        // Query by session_id
        if (filter.getSessionId() != null) {
            for (Task t : tasks.values()) {
                if (filter.getSessionId().equals(t.getSessionId())) {
                    candidateIds.add(t.getTaskId());
                }
            }
        }

        // Query by priority
        if (resolvedPriority != null) {
            List<String> byPriority = priorityIndex.get(resolvedPriority);
            if (byPriority != null) {
                candidateIds.addAll(byPriority);
            }
        }

        // Query by is_root
        if (filter.isRoot()) {
            candidateIds.addAll(rootTasks);
        }

        // If no primary filters, but status/userId, check all
        boolean hasPrimary = (filter.getTaskId() != null || filter.getTaskIds() != null
            || filter.getSessionId() != null || resolvedPriority != null
            || wasHighest || filter.isRoot());
        if (!hasPrimary && (filter.getStatus() != null || filter.getUserId() != null)) {
            candidateIds = new LinkedHashSet<>(tasks.keySet());
        }

        // Apply secondary filters
        List<Task> resultTasks = new ArrayList<>();
        for (String tid : candidateIds) {
            Task task = tasks.get(tid);
            if (task == null) continue;

            if (filter.getStatus() != null && task.getStatus() != filter.getStatus()) continue;
            if (filter.getUserId() != null) {
                Map<String, Object> meta = task.getMetadata();
                if (meta == null || !filter.getUserId().equals(meta.get("user_id"))) continue;
            }

            resultTasks.add(task);
        }

        // Handle with_children
        if (filter.isWithChildren()) {
            Set<String> childIds = new LinkedHashSet<>();
            for (Task t : resultTasks) {
                if (parentToChildren.containsKey(t.getTaskId())) {
                    collectAllChildren(t.getTaskId(), childIds);
                }
            }
            for (String cid : childIds) {
                Task child = tasks.get(cid);
                if (child != null) {
                    resultTasks.add(child);
                }
            }
        }

        return resultTasks;
    }

    /**
     * Internal method to remove tasks and update indices.
     * Caller must hold the lock.
     */
    private void removeTasksInternal(Set<String> taskIdsToRemove) {
        for (String tid : new ArrayList<>(taskIdsToRemove)) {
            Task task = tasks.get(tid);
            if (task == null) continue;

            // Remove from priority index
            List<String> pList = priorityIndex.get(task.getPriority());
            if (pList != null) {
                pList.remove(tid);
            }

            // Remove from hierarchical relationship
            if (task.getParentTaskId() != null) {
                Set<String> siblings = parentToChildren.get(task.getParentTaskId());
                if (siblings != null) {
                    siblings.remove(tid);
                }
                childToParent.remove(tid);
            } else {
                rootTasks.remove(tid);
            }

            // Promote children to root
            Set<String> children = parentToChildren.get(tid);
            if (children != null) {
                for (String childId : new ArrayList<>(children)) {
                    if (!taskIdsToRemove.contains(childId)) {
                        Task childTask = tasks.get(childId);
                        if (childTask != null) {
                            childTask.setParentTaskId(null);
                            rootTasks.add(childId);
                            childToParent.remove(childId);
                        }
                    }
                }
                parentToChildren.remove(tid);
            }

            // Remove task
            tasks.remove(tid);
        }
    }

    /**
     * Copy a task (shallow copy for now).
     */
    private Task copyTask(Task t) {
        return Task.builder(t.getSessionId(), t.getTaskId(), t.getTaskType())
            .description(t.getDescription())
            .priority(t.getPriority())
            .inputs(t.getInputs())
            .outputs(t.getOutputs())
            .status(t.getStatus())
            .parentTaskId(t.getParentTaskId())
            .contextId(t.getContextId())
            .inputRequiredFields(t.getInputRequiredFields())
            .errorMessage(t.getErrorMessage())
            .metadata(t.getMetadata() != null ? new HashMap<>(t.getMetadata()) : null)
            .build();
    }

    private Map<Integer, List<String>> deepCopyPriorityIndex() {
        Map<Integer, List<String>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : priorityIndex.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private Map<String, Set<String>> deepCopyParentToChildren() {
        Map<String, Set<String>> copy = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : parentToChildren.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }
}

