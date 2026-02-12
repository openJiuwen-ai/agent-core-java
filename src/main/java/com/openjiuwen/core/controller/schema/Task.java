// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task Model.
 *
 * <p>Defines the structure of a task, including task basic information, status,
 * input/output, and hierarchical relationships.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class Task {

    private final String sessionId;
    private final String taskId;
    private final String taskType;
    private String description;
    private int priority;
    private List<Event> inputs;
    private List<ControllerOutputChunk> outputs;
    private TaskStatus status;
    private String parentTaskId;
    private String contextId;
    private Object inputRequiredFields;
    private String errorMessage;
    private Map<String, Object> metadata;

    /**
     * Minimal constructor for required fields.
     *
     * @param sessionId the session ID
     * @param taskId    the task ID
     * @param taskType  the task type
     * @param status    the task status
     */
    public Task(String sessionId, String taskId, String taskType, TaskStatus status) {
        this.sessionId = validateRequiredString(sessionId, "sessionId");
        this.taskId = validateRequiredString(taskId, "taskId");
        this.taskType = validateRequiredString(taskType, "taskType");
        this.status = status != null ? status : TaskStatus.UNKNOWN;
        this.priority = 1;
        this.outputs = new ArrayList<>();
    }

    /**
     * Private constructor for Builder.
     */
    private Task(Builder builder) {
        this.sessionId = validateRequiredString(builder.sessionId, "sessionId");
        this.taskId = validateRequiredString(builder.taskId, "taskId");
        this.taskType = validateRequiredString(builder.taskType, "taskType");
        this.description = builder.description;
        this.priority = builder.priority;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs != null ? builder.outputs : new ArrayList<>();
        this.status = builder.status;
        this.parentTaskId = builder.parentTaskId;
        this.contextId = builder.contextId;
        this.inputRequiredFields = builder.inputRequiredFields;
        this.errorMessage = builder.errorMessage;
        this.metadata = builder.metadata;

        // Validate priority
        if (this.priority < 0) {
            throw new IllegalArgumentException("Priority must be a non-negative integer");
        }

        // Validate parent_task_id
        if (this.parentTaskId != null) {
            if (this.parentTaskId.isBlank()) {
                throw new IllegalArgumentException("parent_task_id cannot be an empty string if provided");
            }
            this.parentTaskId = this.parentTaskId.strip();
        }

        // Validate task consistency
        validateTaskConsistency();
    }

    /**
     * Creates a new Builder.
     *
     * @param sessionId the session ID
     * @param taskId    the task ID
     * @param taskType  the task type
     * @return a new Builder
     */
    public static Builder builder(String sessionId, String taskId, String taskType) {
        return new Builder(sessionId, taskId, taskType);
    }

    /**
     * Validates and strips a required string field.
     */
    private static String validateRequiredString(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " field cannot be empty");
        }
        return value.strip();
    }

    /**
     * Validates task consistency and status-specific requirements.
     */
    private void validateTaskConsistency() {
        // Check for circular reference
        if (parentTaskId != null && taskId.equals(parentTaskId)) {
            throw new IllegalArgumentException("task_id cannot be the same as parent_task_id (circular reference)");
        }

        // Validate status-specific fields
        if (status == TaskStatus.FAILED) {
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("error_message is required when status is FAILED");
            }
        }

        if (status == TaskStatus.INPUT_REQUIRED) {
            if (inputRequiredFields == null) {
                throw new IllegalArgumentException("input_required_fields is required when status is INPUT_REQUIRED");
            }
        }
    }

    // Getters

    public String getSessionId() {
        return sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (priority < 0) {
            throw new IllegalArgumentException("Priority must be a non-negative integer");
        }
        this.priority = priority;
    }

    public List<Event> getInputs() {
        return inputs;
    }

    public void setInputs(List<Event> inputs) {
        this.inputs = inputs;
    }

    public List<ControllerOutputChunk> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ControllerOutputChunk> outputs) {
        this.outputs = outputs;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public Object getInputRequiredFields() {
        return inputRequiredFields;
    }

    public void setInputRequiredFields(Object inputRequiredFields) {
        this.inputRequiredFields = inputRequiredFields;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Builder for Task.
     */
    public static class Builder {
        private final String sessionId;
        private final String taskId;
        private final String taskType;
        private String description;
        private int priority = 1;
        private List<Event> inputs;
        private List<ControllerOutputChunk> outputs;
        private TaskStatus status = TaskStatus.UNKNOWN;
        private String parentTaskId;
        private String contextId;
        private Object inputRequiredFields;
        private String errorMessage;
        private Map<String, Object> metadata;

        public Builder(String sessionId, String taskId, String taskType) {
            this.sessionId = sessionId;
            this.taskId = taskId;
            this.taskType = taskType;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder inputs(List<Event> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder outputs(List<ControllerOutputChunk> outputs) {
            this.outputs = outputs;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder parentTaskId(String parentTaskId) {
            this.parentTaskId = parentTaskId;
            return this;
        }

        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public Builder inputRequiredFields(Object inputRequiredFields) {
            this.inputRequiredFields = inputRequiredFields;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds and validates the Task.
         *
         * @return the validated Task
         * @throws IllegalArgumentException if validation fails
         */
        public Task build() {
            return new Task(this);
        }
    }
}

