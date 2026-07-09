/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

import com.openjiuwen.core.workflow.Workflow;

import java.util.function.Supplier;

/**
 * Provider functional interface for creating Workflow instances.
 * Mirrors Python's {@code WorkflowProvider = Callable[..., Workflow]}.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface WorkflowProvider extends Supplier<Workflow> {
}
