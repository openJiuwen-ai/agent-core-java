/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.contexts;

import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.schema.OptimizationTask;

/**
 * Public class TaskContext used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TaskContext extends SessionContext {
    private final OptimizationTask task;
    private final TaskRuntime runtime;

    /**
     * TaskContext.
     * 
     * @param orchestrator orchestrator
     * @param task task
     * @param runtime runtime
     * @since 0.1.7
     */
    public TaskContext(AutoHarnessOrchestrator orchestrator, OptimizationTask task, TaskRuntime runtime) {
        super(orchestrator);
        this.task = task;
        this.runtime = runtime;
    }

    /**
     * taskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String taskId() {
        return taskKey(task);
    }

    /**
     * taskKey.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    public static String taskKey(OptimizationTask task) {
        return task != null && task.getTopic() != null && !task.getTopic().isBlank() ? task.getTopic() : "task";
    }

    /**
     * getTask.
     * 
     * @return the result
     * @since 0.1.7
     */
    public OptimizationTask getTask() {
        return task;
    }

    /**
     * getRuntime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskRuntime getRuntime() {
        return runtime;
    }
}
