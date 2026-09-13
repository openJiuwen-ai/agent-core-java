/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

/**
 * Task failed event.
 * <p>
 * Generated when task execution fails, containing error information.
 * <p>
 * Mirrors Python's {@code TaskFailedEvent}.
 * 
 * @since 0.1.7
 */
public class TaskFailedEvent extends Event {
    private String errorMessage;
    private Task task;

    /**
     * TaskFailedEvent.
     * 
     * @since 0.1.7
     */
    public TaskFailedEvent() {
        super(EventType.TASK_FAILED);
    }

    /**
     * TaskFailedEvent.
     * 
     * @param errorMessage errorMessage
     * @param task task
     * @since 0.1.7
     */
    public TaskFailedEvent(String errorMessage, Task task) {
        super(EventType.TASK_FAILED);
        this.errorMessage = errorMessage;
        this.task = task;
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
     * getTask.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Task getTask() {
        return task;
    }

    /**
     * setTask.
     * 
     * @param task task
     * @since 0.1.7
     */
    public void setTask(Task task) {
        this.task = task;
    }
}
