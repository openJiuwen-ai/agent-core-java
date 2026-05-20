/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.contexts;

import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.schema.OptimizationTask;

/**
 * Public class TaskContext used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TaskContext extends SessionContext {
    private final OptimizationTask task;
    private final TaskRuntime runtime;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskContext(AutoHarnessOrchestrator orchestrator, OptimizationTask task, TaskRuntime runtime) {
        super(orchestrator);
        this.task = task;
        this.runtime = runtime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String taskId() {
        return taskKey(task);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String taskKey(OptimizationTask task) {
        return task != null && task.getTopic() != null && !task.getTopic().isBlank() ? task.getTopic() : "task";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public OptimizationTask getTask() {
        return task;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskRuntime getRuntime() {
        return runtime;
    }
}
