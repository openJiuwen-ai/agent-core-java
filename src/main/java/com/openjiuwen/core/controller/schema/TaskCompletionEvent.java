// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task Completion Event.
 *
 * <p>Event generated when task execution is completed, containing task results.
 * This event is generated when a task successfully completes and includes the
 * task's output results.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskCompletionEvent extends Event {

    private final List<BaseDataFrame> taskResult;
    private final Task task;

    /**
     * Default constructor.
     */
    public TaskCompletionEvent() {
        this(new ArrayList<>(), null);
    }

    /**
     * Constructor with results and task.
     *
     * @param taskResult the task result list
     * @param task       the associated task (can be null)
     */
    public TaskCompletionEvent(List<BaseDataFrame> taskResult, Task task) {
        super(EventType.TASK_COMPLETION);
        this.taskResult = taskResult != null ? new ArrayList<>(taskResult) : new ArrayList<>();
        this.task = task;
    }

    /**
     * Full constructor with all event fields.
     *
     * @param eventId    the event ID
     * @param metadata   the metadata
     * @param taskResult the task result list
     * @param task       the associated task
     */
    public TaskCompletionEvent(String eventId, Map<String, Object> metadata,
                                List<BaseDataFrame> taskResult, Task task) {
        super(EventType.TASK_COMPLETION, eventId, metadata);
        this.taskResult = taskResult != null ? new ArrayList<>(taskResult) : new ArrayList<>();
        this.task = task;
    }

    /**
     * Gets the task results.
     *
     * @return the task result list
     */
    public List<BaseDataFrame> getTaskResult() {
        return taskResult;
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

