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
 * 
 * @since 0.1.7
 */
public class TaskFilter {
    private Object taskId; // String or List<String>
    private String sessionId;
    private String userId;
    private Object priority; // Integer or "highest"
    private TaskStatus status;
    private boolean withChildren;
    private boolean isRoot;

    /**
     * TaskFilter.
     * 
     * @since 0.1.7
     */
    private TaskFilter() {
    }

    /**
     * Validate that at least one filter parameter is provided.
     * 
     * @since 0.1.7
     */
    private void validate() {
        boolean allEmpty =
            taskId == null && sessionId == null && userId == null && priority == null && status == null && !isRoot;
        if (allEmpty) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR, "error_msg",
                    "At least one filter parameter must be provided");
        }
    }

    // Static factory methods for common filters

    /**
     * byTaskId.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public static TaskFilter byTaskId(String taskId) {
        TaskFilter f = new TaskFilter();
        f.taskId = taskId;
        return f;
    }

    /**
     * byTaskIds.
     * 
     * @param taskIds taskIds
     * @return the result
     * @since 0.1.7
     */
    public static TaskFilter byTaskIds(List<String> taskIds) {
        TaskFilter f = new TaskFilter();
        f.taskId = taskIds;
        return f;
    }

    /**
     * bySessionId.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public static TaskFilter bySessionId(String sessionId) {
        TaskFilter f = new TaskFilter();
        f.sessionId = sessionId;
        return f;
    }

    /**
     * byStatus.
     * 
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public static TaskFilter byStatus(TaskStatus status) {
        TaskFilter f = new TaskFilter();
        f.status = status;
        return f;
    }

    /**
     * byRoot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static TaskFilter byRoot() {
        TaskFilter f = new TaskFilter();
        f.isRoot = true;
        return f;
    }

    /**
     * byHighestPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static TaskFilter byHighestPriority() {
        TaskFilter f = new TaskFilter();
        f.priority = "highest";
        return f;
    }

    /**
     * General-purpose builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    /**
     * getTaskIdList.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public List<String> getTaskIdList() {
        if (taskId == null) {
            return java.util.Collections.emptyList();
        }
        if (taskId instanceof String s) {
            return List.of(s);
        }
        return (List<String>) taskId;
    }

    /**
     * getTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getTaskId() {
        return taskId;
    }

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * getUserId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUserId() {
        return userId;
    }

    /**
     * getPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getPriority() {
        return priority;
    }

    /**
     * getPriorityAsInt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getPriorityAsInt() {
        if (priority instanceof Integer i) {
            return i;
        }
        return null;
    }

    /**
     * isHighestPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isHighestPriority() {
        return "highest".equals(priority);
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * isWithChildren.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isWithChildren() {
        return withChildren;
    }

    /**
     * isRoot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRoot() {
        return isRoot;
    }

    /**
     * Builder for TaskFilter.
     * 
     * @since 0.1.7
     */
    public static class Builder {
        private final TaskFilter filter = new TaskFilter();

        /**
         * taskId.
         * 
         * @param taskId taskId
         * @return the result
         * @since 0.1.7
         */
        public Builder taskId(String taskId) {
            filter.taskId = taskId;
            return this;
        }

        /**
         * taskIds.
         * 
         * @param taskIds taskIds
         * @return the result
         * @since 0.1.7
         */
        public Builder taskIds(List<String> taskIds) {
            filter.taskId = taskIds;
            return this;
        }

        /**
         * sessionId.
         * 
         * @param sessionId sessionId
         * @return the result
         * @since 0.1.7
         */
        public Builder sessionId(String sessionId) {
            filter.sessionId = sessionId;
            return this;
        }

        /**
         * userId.
         * 
         * @param userId userId
         * @return the result
         * @since 0.1.7
         */
        public Builder userId(String userId) {
            filter.userId = userId;
            return this;
        }

        /**
         * priority.
         * 
         * @param priority priority
         * @return the result
         * @since 0.1.7
         */
        public Builder priority(int priority) {
            filter.priority = priority;
            return this;
        }

        /**
         * highestPriority.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Builder highestPriority() {
            filter.priority = "highest";
            return this;
        }

        /**
         * status.
         * 
         * @param status status
         * @return the result
         * @since 0.1.7
         */
        public Builder status(TaskStatus status) {
            filter.status = status;
            return this;
        }

        /**
         * withChildren.
         * 
         * @param withChildren withChildren
         * @return the result
         * @since 0.1.7
         */
        public Builder withChildren(boolean withChildren) {
            filter.withChildren = withChildren;
            return this;
        }

        /**
         * isRoot.
         * 
         * @param isRoot isRoot
         * @return the result
         * @since 0.1.7
         */
        public Builder isRoot(boolean isRoot) {
            filter.isRoot = isRoot;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public TaskFilter build() {
            filter.validate();
            return filter;
        }
    }
}
