/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;

import java.util.Objects;

/**
 * A verified extension task ready for serial activation.
 *
 * <p>Mirrors Python's {@code VerifiedExtensionTask} in
 * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extension_task_pipeline.py}.</p>
 *
 * @param design extension design
 * @param task task wrapper
 * @param ctx task context holding runtime artifacts
 */
public record VerifiedExtensionTask(ExtensionDesign design, OptimizationTask task, TaskContext ctx) {

    public VerifiedExtensionTask {
        Objects.requireNonNull(design, "design must not be null");
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");
    }
}
