/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.OptimizationTask;

/**
 * Runtime context passed into task pipelines and stages.
 *
 * <p>Mirrors Python's {@code TaskContext} in {@code openjiuwen.auto_harness.contexts.execution}.</p>
 */
public class TaskContext extends SessionContext {

    private OptimizationTask task;
    private TaskRuntime runtime;

    public TaskContext(AutoHarnessOrchestrator orchestrator, OptimizationTask task, TaskRuntime runtime) {
        super(orchestrator);
        this.task = task;
        this.runtime = runtime;
    }

    /**
     * Get the task ID for this context.
     *
     * @return the task key derived from task topic
     */
    @Override
    public String getTaskId() {
        return taskKey(task);
    }

    /**
     * Return the scoped artifact key for a task.
     *
     * @param task the optimization task
     * @return the task key
     */
    public static String taskKey(OptimizationTask task) {
        String topic = task.getTopic();
        return (topic != null && !topic.isEmpty()) ? topic : "task";
    }

    /**
     * Get the optimization task.
     *
     * @return the task
     */
    public OptimizationTask getTask() {
        return task;
    }

    /**
     * Set the optimization task.
     *
     * @param task the task
     */
    public void setTask(OptimizationTask task) {
        this.task = task;
    }

    /**
     * Get the task runtime.
     *
     * @return the runtime
     */
    public TaskRuntime getRuntime() {
        return runtime;
    }

    /**
     * Set the task runtime.
     *
     * @param runtime the runtime
     */
    public void setRuntime(TaskRuntime runtime) {
        this.runtime = runtime;
    }
}