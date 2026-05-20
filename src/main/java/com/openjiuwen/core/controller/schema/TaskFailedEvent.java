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
 */
public class TaskFailedEvent extends Event {

    private String errorMessage;
    private Task task;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskFailedEvent() {
        super(EventType.TASK_FAILED);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskFailedEvent(String errorMessage, Task task) {
        super(EventType.TASK_FAILED);
        this.errorMessage = errorMessage;
        this.task = task;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Task getTask() {
        return task;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTask(Task task) {
        this.task = task;
    }
}
