// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.Map;

/**
 * Task Failed Event.
 *
 * <p>Event generated when task execution fails, containing error information.
 * This event is generated when an error occurs during task execution and includes
 * error details.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskFailedEvent extends Event {

    private final String errorMessage;
    private final Task task;

    /**
     * Default constructor.
     */
    public TaskFailedEvent() {
        this(null, null);
    }

    /**
     * Constructor with error message and task.
     *
     * @param errorMessage the error message (can be null)
     * @param task         the associated task (can be null)
     */
    public TaskFailedEvent(String errorMessage, Task task) {
        super(EventType.TASK_FAILED);
        this.errorMessage = errorMessage;
        this.task = task;
    }

    /**
     * Full constructor with all event fields.
     *
     * @param eventId      the event ID
     * @param metadata     the metadata
     * @param errorMessage the error message
     * @param task         the associated task
     */
    public TaskFailedEvent(String eventId, Map<String, Object> metadata,
                            String errorMessage, Task task) {
        super(EventType.TASK_FAILED, eventId, metadata);
        this.errorMessage = errorMessage;
        this.task = task;
    }

    /**
     * Gets the error message.
     *
     * @return the error message, or null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the associated task.
     *
     * @return the task, or null
     */
    public Task getTask() {
        return task;
    }
}

