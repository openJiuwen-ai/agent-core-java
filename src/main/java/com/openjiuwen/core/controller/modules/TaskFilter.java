// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.schema.TaskStatus;

import java.util.List;
import java.util.Map;

/**
 * Task Filter.
 *
 * <p>Used to filter tasks in getTask, removeTask, and popTask methods.
 * All fields are optional and can be combined for complex filtering.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskFilter {

    /** Single task ID filter. */
    private final String taskId;
    /** List of task IDs filter. */
    private final List<String> taskIds;
    /** Session ID filter. */
    private final String sessionId;
    /** User ID filter (looked up in metadata). */
    private final String userId;
    /** Priority filter (integer value). */
    private final Integer priority;
    /** Whether priority is "highest" (special sentinel). */
    private final boolean priorityHighest;
    /** Task status filter. */
    private final TaskStatus status;
    /** Whether to include child tasks. */
    private final boolean withChildren;
    /** Whether to only query root tasks. */
    private final boolean isRoot;

    private TaskFilter(TaskFilterBuilder builder) {
        this.taskId = builder.taskId;
        this.taskIds = builder.taskIds;
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.priority = builder.priority;
        this.priorityHighest = builder.priorityHighest;
        this.status = builder.status;
        this.withChildren = builder.withChildren;
        this.isRoot = builder.isRoot;

        // Validate that at least one filter parameter is set
        boolean allEmpty = (taskId == null && taskIds == null && sessionId == null
            && userId == null && priority == null && !priorityHighest
            && status == null && !isRoot);
        if (allEmpty) {
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                null, null, null,
                Map.of("error_msg", "At least one filter parameter (task_id, session_id, "
                    + "user_id, priority, status, or is_root) must be provided")
            );
        }
    }

    /**
     * Creates a new builder.
     *
     * @return a new TaskFilterBuilder
     */
    public static TaskFilterBuilder builder() {
        return new TaskFilterBuilder();
    }

    // Getters

    /**
     * Gets the single task ID (or null if list-based).
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Gets the list of task IDs (or null if single-based).
     */
    public List<String> getTaskIds() {
        return taskIds;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public Integer getPriority() {
        return priority;
    }

    public boolean isPriorityHighest() {
        return priorityHighest;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public boolean isWithChildren() {
        return withChildren;
    }

    public boolean isRoot() {
        return isRoot;
    }

    /**
     * Builder for TaskFilter.
     */
    public static class TaskFilterBuilder {
        private String taskId;
        private List<String> taskIds;
        private String sessionId;
        private String userId;
        private Integer priority;
        private boolean priorityHighest = false;
        private TaskStatus status;
        private boolean withChildren = false;
        private boolean isRoot = false;

        public TaskFilterBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public TaskFilterBuilder taskIds(List<String> taskIds) {
            this.taskIds = taskIds;
            return this;
        }

        public TaskFilterBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public TaskFilterBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public TaskFilterBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public TaskFilterBuilder priorityHighest() {
            this.priorityHighest = true;
            return this;
        }

        public TaskFilterBuilder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public TaskFilterBuilder withChildren(boolean withChildren) {
            this.withChildren = withChildren;
            return this;
        }

        public TaskFilterBuilder isRoot(boolean isRoot) {
            this.isRoot = isRoot;
            return this;
        }

        /**
         * Builds and validates the TaskFilter.
         *
         * @return the validated TaskFilter
         */
        public TaskFilter build() {
            return new TaskFilter(this);
        }
    }
}

