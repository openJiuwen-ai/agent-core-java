/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

    public TaskFailedEvent() {
        super(EventType.TASK_FAILED);
    }

    public TaskFailedEvent(String errorMessage, Task task) {
        super(EventType.TASK_FAILED);
        this.errorMessage = errorMessage;
        this.task = task;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
