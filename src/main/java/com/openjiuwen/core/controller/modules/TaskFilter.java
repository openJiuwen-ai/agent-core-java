/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    public static TaskFilter byTaskId(String taskId) {
        TaskFilter f = new TaskFilter();
        f.taskId = taskId;
        return f;
    }

    public static TaskFilter byTaskIds(List<String> taskIds) {
        TaskFilter f = new TaskFilter();
        f.taskId = taskIds;
        return f;
    }

    public static TaskFilter bySessionId(String sessionId) {
        TaskFilter f = new TaskFilter();
        f.sessionId = sessionId;
        return f;
    }

    public static TaskFilter byStatus(TaskStatus status) {
        TaskFilter f = new TaskFilter();
        f.status = status;
        return f;
    }

    public static TaskFilter byRoot() {
        TaskFilter f = new TaskFilter();
        f.isRoot = true;
        return f;
    }

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
    public List<String> getTaskIdList() {
        if (taskId == null) {
            return null;
        }
        if (taskId instanceof String s) {
            return List.of(s);
        }
        return (List<String>) taskId;
    }

    public Object getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public Object getPriority() {
        return priority;
    }

    public Integer getPriorityAsInt() {
        if (priority instanceof Integer i) {
            return i;
        }
        return null;
    }

    public boolean isHighestPriority() {
        return "highest".equals(priority);
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
    public static class Builder {
        private final TaskFilter filter = new TaskFilter();

        public Builder taskId(String taskId) {
            filter.taskId = taskId;
            return this;
        }

        public Builder taskIds(List<String> taskIds) {
            filter.taskId = taskIds;
            return this;
        }

        public Builder sessionId(String sessionId) {
            filter.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            filter.userId = userId;
            return this;
        }

        public Builder priority(int priority) {
            filter.priority = priority;
            return this;
        }

        public Builder highestPriority() {
            filter.priority = "highest";
            return this;
        }

        public Builder status(TaskStatus status) {
            filter.status = status;
            return this;
        }

        public Builder withChildren(boolean withChildren) {
            filter.withChildren = withChildren;
            return this;
        }

        public Builder isRoot(boolean isRoot) {
            filter.isRoot = isRoot;
            return this;
        }

        public TaskFilter build() {
            filter.validate();
            return filter;
        }
    }
}
