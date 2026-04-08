/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Task completion event.
 * <p>
 * Generated when task execution is completed, containing task results.
 * <p>
 * Mirrors Python's {@code TaskCompletionEvent}.
 */
public class TaskCompletionEvent extends Event {

    private List<DataFrame> taskResult;
    private Task task;

    public TaskCompletionEvent() {
        super(EventType.TASK_COMPLETION);
        this.taskResult = new ArrayList<>();
    }

    public TaskCompletionEvent(List<DataFrame> taskResult, Task task) {
        super(EventType.TASK_COMPLETION);
        this.taskResult = taskResult != null ? taskResult : new ArrayList<>();
        this.task = task;
    }

    public List<DataFrame> getTaskResult() {
        return taskResult;
    }

    public void setTaskResult(List<DataFrame> taskResult) {
        this.taskResult = taskResult != null ? taskResult : new ArrayList<>();
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
