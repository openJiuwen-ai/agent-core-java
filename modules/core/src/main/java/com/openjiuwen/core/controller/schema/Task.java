/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task data model.
 * <p>
 * Defines the structure of a task, including basic information, status,
 * input/output, and hierarchical relationships.
 * <p>
 * Mirrors Python's {@code Task(BaseModel)}.
 * 
 * @since 0.1.7
 */
public class Task {
    private String sessionId;
    private String taskId;
    private String taskType;
    private String description;
    private int priority;
    private List<Object> inputs;
    private List<ControllerOutputChunk> outputs;
    private TaskStatus status;
    private String parentTaskId;
    private String contextId;
    private Object inputRequiredFields;
    private String errorMessage;
    private Map<String, Object> metadata;
    private Map<String, Object> extensions;

    /**
     * Task.
     * 
     * @since 0.1.7
     */
    public Task() {
        this.priority = 1;
        this.outputs = new ArrayList<>();
        this.status = TaskStatus.UNKNOWN;
    }

    /**
     * Task.
     * 
     * @param sessionId sessionId
     * @param taskId taskId
     * @param taskType taskType
     * @since 0.1.7
     */
    public Task(String sessionId, String taskId, String taskType) {
        this();
        validateRequiredString(sessionId, "sessionId");
        validateRequiredString(taskId, "taskId");
        validateRequiredString(taskType, "taskType");
        this.sessionId = sessionId.strip();
        this.taskId = taskId.strip();
        this.taskType = taskType.strip();
    }

    /**
     * Deep copy of this task.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Task copy() {
        Task copy = new Task();
        copy.sessionId = this.sessionId;
        copy.taskId = this.taskId;
        copy.taskType = this.taskType;
        copy.description = this.description;
        copy.priority = this.priority;
        copy.inputs = this.inputs != null ? new ArrayList<Object>(this.inputs) : null;
        copy.outputs = new ArrayList<>(this.outputs);
        copy.status = this.status;
        copy.parentTaskId = this.parentTaskId;
        copy.contextId = this.contextId;
        copy.inputRequiredFields = this.inputRequiredFields;
        copy.errorMessage = this.errorMessage;
        copy.metadata = this.metadata != null ? new HashMap<>(this.metadata) : null;
        copy.extensions = this.extensions != null ? new HashMap<>(this.extensions) : null;
        return copy;
    }

    /**
     * Validate the task for consistency.
     * 
     * @since 0.1.7
     */
    public void validate() {
        validateRequiredString(sessionId, "sessionId");
        validateRequiredString(taskId, "taskId");
        validateRequiredString(taskType, "taskType");

        if (priority < 0) {
            throw new IllegalArgumentException("Priority must be a non-negative integer");
        }

        if (parentTaskId != null && parentTaskId.isBlank()) {
            throw new IllegalArgumentException("parent_task_id cannot be an empty string if provided");
        }

        if (parentTaskId != null && taskId.equals(parentTaskId)) {
            throw new IllegalArgumentException("task_id cannot be the same as parent_task_id (circular reference)");
        }

        if (status == TaskStatus.FAILED && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("error_message is required when status is FAILED");
        }

        if (status == TaskStatus.INPUT_REQUIRED && inputRequiredFields == null) {
            throw new IllegalArgumentException("input_required_fields is required when status is INPUT_REQUIRED");
        }
    }

    /**
     * validateRequiredString.
     * 
     * @param value value
     * @param fieldName fieldName
     * @since 0.1.7
     */
    private static void validateRequiredString(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " field cannot be empty");
        }
    }

    // Getters and setters

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
     * setSessionId.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * getTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * setTaskId.
     * 
     * @param taskId taskId
     * @since 0.1.7
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * getTaskType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * setTaskType.
     * 
     * @param taskType taskType
     * @since 0.1.7
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * setDescription.
     * 
     * @param description description
     * @since 0.1.7
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * getPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getPriority() {
        return priority;
    }

    /**
     * setPriority.
     * 
     * @param priority priority
     * @since 0.1.7
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * getInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getInputs() {
        return inputs;
    }

    /**
     * setInputs.
     * 
     * @param inputs inputs
     * @since 0.1.7
     */
    public void setInputs(List<Object> inputs) {
        this.inputs = inputs;
    }

    /**
     * getOutputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ControllerOutputChunk> getOutputs() {
        return outputs;
    }

    /**
     * setOutputs.
     * 
     * @param outputs outputs
     * @since 0.1.7
     */
    public void setOutputs(List<ControllerOutputChunk> outputs) {
        this.outputs = outputs;
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
     * setStatus.
     * 
     * @param status status
     * @since 0.1.7
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /**
     * getParentTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getParentTaskId() {
        return parentTaskId;
    }

    /**
     * setParentTaskId.
     * 
     * @param parentTaskId parentTaskId
     * @since 0.1.7
     */
    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId != null && !parentTaskId.isBlank() ? parentTaskId.strip() : null;
    }

    /**
     * getContextId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContextId() {
        return contextId;
    }

    /**
     * setContextId.
     * 
     * @param contextId contextId
     * @since 0.1.7
     */
    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    /**
     * getInputRequiredFields.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getInputRequiredFields() {
        return inputRequiredFields;
    }

    /**
     * setInputRequiredFields.
     * 
     * @param inputRequiredFields inputRequiredFields
     * @since 0.1.7
     */
    public void setInputRequiredFields(Object inputRequiredFields) {
        this.inputRequiredFields = inputRequiredFields;
    }

    /**
     * getErrorMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * setErrorMessage.
     * 
     * @param errorMessage errorMessage
     * @since 0.1.7
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * getExtensions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * setExtensions.
     * 
     * @param extensions extensions
     * @since 0.1.7
     */
    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    /**
     * Serialize task to a plain map for persistence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id", sessionId);
        map.put("task_id", taskId);
        map.put("task_type", taskType);
        map.put("description", description);
        map.put("priority", priority);
        map.put("status", status != null ? status.name() : null);
        map.put("parent_task_id", parentTaskId);
        map.put("context_id", contextId);
        map.put("error_message", errorMessage);
        map.put("metadata", metadata);
        map.put("extensions", extensions);
        return map;
    }

    /**
     * fromMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static Task fromMap(Map<String, Object> map) {
        Task task = new Task();
        task.setSessionId((String) map.get("session_id"));
        task.setTaskId((String) map.get("task_id"));
        task.setTaskType((String) map.get("task_type"));
        task.setDescription((String) map.get("description"));
        if (map.get("priority") != null) {
            task.setPriority(((Number) map.get("priority")).intValue());
        }
        if (map.get("status") != null) {
            task.setStatus(TaskStatus.valueOf((String) map.get("status")));
        }
        task.setParentTaskId((String) map.get("parent_task_id"));
        task.setContextId((String) map.get("context_id"));
        task.setErrorMessage((String) map.get("error_message"));
        task.setMetadata((Map<String, Object>) map.get("metadata"));
        task.setExtensions((Map<String, Object>) map.get("extensions"));
        return task;
    }
}
