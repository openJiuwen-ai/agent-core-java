/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task data model.
 * <p>
 * Defines the structure of a task, including basic information, status,
 * input/output, and hierarchical relationships.
 * <p>
 * Mirrors Python's {@code Task} in
 * {@code openjiuwen/core/controller/schema/task.py}.
 */
public class Task {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("task_type")
    private String taskType;

    private String description;

    private int priority;

    private List<Event> inputs;

    private List<ControllerOutputChunk> outputs;

    private TaskStatus status;

    @JsonProperty("parent_task_id")
    private String parentTaskId;

    @JsonProperty("context_id")
    private String contextId;

    @JsonProperty("input_required_fields")
    private Object inputRequiredFields;

    @JsonProperty("error_message")
    private String errorMessage;

    private Map<String, Object> metadata;

    private Map<String, Object> extensions;

    public Task() {
        this.priority = 1;
        this.outputs = new ArrayList<>();
        this.status = TaskStatus.UNKNOWN;
    }

    public Task(String sessionId, String taskId, String taskType) {
        this();
        setSessionId(sessionId);
        setTaskId(taskId);
        setTaskType(taskType);
    }

    /**
     * Deep copy of this task.
     */
    public Task copy() {
        Task copy = new Task();
        copy.sessionId = this.sessionId;
        copy.taskId = this.taskId;
        copy.taskType = this.taskType;
        copy.description = this.description;
        copy.priority = this.priority;
        copy.inputs = this.inputs != null ? new ArrayList<>(this.inputs) : null;
        copy.outputs = new ArrayList<>(this.outputs);
        copy.status = this.status;
        copy.parentTaskId = this.parentTaskId;
        copy.contextId = this.contextId;
        copy.inputRequiredFields = this.inputRequiredFields;
        copy.errorMessage = this.errorMessage;
        copy.metadata = this.metadata != null ? new LinkedHashMap<>(this.metadata) : null;
        copy.extensions = this.extensions != null ? new LinkedHashMap<>(this.extensions) : null;
        return copy;
    }

    /**
     * Validate the task for consistency.
     */
    public void validate() {
        validateRequiredString(sessionId);
        validateRequiredString(taskId);
        validateRequiredString(taskType);

        if (priority < 0) {
            throw new IllegalArgumentException("Priority must be a non-negative integer");
        }

        if (outputs == null) {
            throw new IllegalArgumentException("outputs cannot be null");
        }

        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
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

    private static void validateRequiredString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task field cannot be empty");
        }
    }

    private static String normalizeRequiredString(String value) {
        validateRequiredString(value);
        return value.strip();
    }

    // Getters and setters

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = normalizeRequiredString(sessionId);
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = normalizeRequiredString(taskId);
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = normalizeRequiredString(taskType);
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
        this.inputs = inputs != null ? new ArrayList<>(inputs) : null;
    }

    public List<ControllerOutputChunk> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ControllerOutputChunk> outputs) {
        if (outputs == null) {
            throw new IllegalArgumentException("outputs cannot be null");
        }
        this.outputs = new ArrayList<>(outputs);
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        this.status = status;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        if (parentTaskId != null && parentTaskId.isBlank()) {
            throw new IllegalArgumentException("parent_task_id cannot be an empty string if provided");
        }
        this.parentTaskId = parentTaskId != null ? parentTaskId.strip() : null;
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
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : null;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions != null ? new LinkedHashMap<>(extensions) : null;
    }

    /**
     * Serialize task to a plain map for persistence.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id", sessionId);
        map.put("task_id", taskId);
        map.put("task_type", taskType);
        map.put("description", description);
        map.put("priority", priority);
        map.put("inputs", inputs);
        map.put("outputs", outputs);
        map.put("status", status != null ? status.getValue() : null);
        map.put("parent_task_id", parentTaskId);
        map.put("context_id", contextId);
        map.put("input_required_fields", inputRequiredFields);
        map.put("error_message", errorMessage);
        map.put("metadata", metadata);
        map.put("extensions", extensions);
        return map;
    }

    /**
     * Deserialize task from a plain map.
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
        if (map.containsKey("inputs")) {
            task.setInputs(coerceEvents(map.get("inputs")));
        }
        if (map.containsKey("outputs")) {
            task.setOutputs(coerceOutputChunks(map.get("outputs")));
        }
        if (map.get("status") != null) {
            task.setStatus(TaskStatus.fromValue((String) map.get("status")));
        }
        task.setParentTaskId((String) map.get("parent_task_id"));
        task.setContextId((String) map.get("context_id"));
        task.setInputRequiredFields(map.get("input_required_fields"));
        task.setErrorMessage((String) map.get("error_message"));
        task.setMetadata((Map<String, Object>) map.get("metadata"));
        task.setExtensions((Map<String, Object>) map.get("extensions"));
        return task;
    }

    private static List<Event> coerceEvents(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("inputs must be a list when provided");
        }
        List<Event> events = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            events.add(coerceEvent(item));
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private static Event coerceEvent(Object value) {
        if (value instanceof Event event) {
            return event;
        }
        if (value instanceof Map<?, ?> map) {
            Event event = new Event();
            Object eventType = map.get("event_type");
            if (eventType == null) {
                eventType = map.get("eventType");
            }
            if (eventType != null) {
                event.setEventType(EventType.fromValue(String.valueOf(eventType)));
            }
            Object eventId = map.get("event_id");
            if (eventId == null) {
                eventId = map.get("eventId");
            }
            if (eventId != null) {
                event.setEventId(String.valueOf(eventId));
            }
            Object metadata = map.get("metadata");
            if (metadata instanceof Map<?, ?> metadataMap) {
                event.setMetadata((Map<String, Object>) metadataMap);
            } else if (metadata == null) {
                event.setMetadata(null);
            } else {
                throw new IllegalArgumentException("event metadata must be a map when provided");
            }
            return event;
        }
        throw new IllegalArgumentException("inputs must contain Event values");
    }

    private static List<ControllerOutputChunk> coerceOutputChunks(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("outputs cannot be null");
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("outputs must be a list");
        }
        List<ControllerOutputChunk> chunks = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof ControllerOutputChunk chunk) {
                chunks.add(chunk);
            } else {
                throw new IllegalArgumentException("outputs must contain ControllerOutputChunk values");
            }
        }
        return chunks;
    }
}
