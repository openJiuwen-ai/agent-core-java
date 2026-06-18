/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;

/**
 * Runtime context passed into task pipelines and stages.
 *
 * <p>Mirrors Python's {@code TaskContext} in
 * {@code openjiuwen/auto_harness/contexts/execution.py}.</p>
 */
public class TaskContext extends SessionContext {

    private OptimizationTask task;
    private TaskRuntime runtime;

    public TaskContext(AutoHarnessOrchestrator orchestrator, OptimizationTask task, TaskRuntime runtime) {
        super(orchestrator);
        this.task = task;
        this.runtime = runtime;
    }

    @Override
    public String getTaskId() {
        return taskKey(task);
    }

    public static String taskKey(OptimizationTask task) {
        String topic = task == null ? null : task.getTopic();
        return topic == null || topic.isEmpty() ? "task" : topic;
    }

    public OptimizationTask getTask() {
        return task;
    }

    public void setTask(OptimizationTask task) {
        this.task = task;
    }

    public TaskRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(TaskRuntime runtime) {
        this.runtime = runtime;
    }
}
