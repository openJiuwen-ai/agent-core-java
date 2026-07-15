/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * 
 * @since 0.1.7
 */
public class TaskCompletionEvent extends Event {
    private List<DataFrame> taskResult;
    private Task task;

    /**
     * TaskCompletionEvent.
     * 
     * @since 0.1.7
     */
    public TaskCompletionEvent() {
        super(EventType.TASK_COMPLETION);
        this.taskResult = new ArrayList<>();
    }

    /**
     * TaskCompletionEvent.
     * 
     * @param taskResult taskResult
     * @param task task
     * @since 0.1.7
     */
    public TaskCompletionEvent(List<DataFrame> taskResult, Task task) {
        super(EventType.TASK_COMPLETION);
        this.taskResult = taskResult != null ? taskResult : new ArrayList<>();
        this.task = task;
    }

    /**
     * getTaskResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<DataFrame> getTaskResult() {
        return taskResult;
    }

    /**
     * setTaskResult.
     * 
     * @param taskResult taskResult
     * @since 0.1.7
     */
    public void setTaskResult(List<DataFrame> taskResult) {
        this.taskResult = taskResult != null ? taskResult : new ArrayList<>();
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
