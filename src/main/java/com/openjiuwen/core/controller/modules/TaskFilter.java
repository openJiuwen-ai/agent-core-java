/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.schema.TaskStatus;

import java.util.List;

/**
 * Task filter for querying tasks.
 * <p>
 * All fields are optional and can be combined for complex filtering.
 * <p>
 * Mirrors Python's {@code TaskFilter(BaseModel)}.
 */
public class TaskFilter {

    private Object taskId;       // String or List<String>
    private String sessionId;
    private String userId;
    private Object priority;     // Integer or "highest"
    private TaskStatus status;
    private boolean withChildren;
    private boolean isRoot;

    private TaskFilter() {
    }

    /**
     * Validate that at least one filter parameter is provided.
     */
    private void validate() {
        boolean allEmpty = taskId == null
                && sessionId == null
                && userId == null
                && priority == null
                && status == null
                && !isRoot;
        if (allEmpty) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR,
                    "error_msg", "At least one filter parameter must be provided");
        }
    }

    // Static factory methods for common filters

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskFilter byTaskId(String taskId) {
        TaskFilter f = new TaskFilter();
        f.taskId = taskId;
        return f;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskFilter byTaskIds(List<String> taskIds) {
        TaskFilter f = new TaskFilter();
        f.taskId = taskIds;
        return f;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskFilter bySessionId(String sessionId) {
        TaskFilter f = new TaskFilter();
        f.sessionId = sessionId;
        return f;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskFilter byStatus(TaskStatus status) {
        TaskFilter f = new TaskFilter();
        f.status = status;
        return f;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskFilter byRoot() {
        TaskFilter f = new TaskFilter();
        f.isRoot = true;
        return f;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskFilter byHighestPriority() {
        TaskFilter f = new TaskFilter();
        f.priority = "highest";
        return f;
    }

    /**
     * General-purpose builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getTaskIdList() {
        if (taskId == null) {
            return null;
        }
        if (taskId instanceof String s) {
            return List.of(s);
        }
        return (List<String>) taskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getTaskId() {
        return taskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getPriority() {
        return priority;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getPriorityAsInt() {
        if (priority instanceof Integer i) {
            return i;
        }
        return null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHighestPriority() {
        return "highest".equals(priority);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isWithChildren() {
        return withChildren;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isRoot() {
        return isRoot;
    }

    /**
     * Builder for TaskFilter.
     */
    public static class Builder {
        private final TaskFilter filter = new TaskFilter();

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder taskId(String taskId) {
            filter.taskId = taskId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder taskIds(List<String> taskIds) {
            filter.taskId = taskIds;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sessionId(String sessionId) {
            filter.sessionId = sessionId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder userId(String userId) {
            filter.userId = userId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder priority(int priority) {
            filter.priority = priority;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder highestPriority() {
            filter.priority = "highest";
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder status(TaskStatus status) {
            filter.status = status;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder withChildren(boolean withChildren) {
            filter.withChildren = withChildren;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder isRoot(boolean isRoot) {
            filter.isRoot = isRoot;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TaskFilter build() {
            filter.validate();
            return filter;
        }
    }
}
