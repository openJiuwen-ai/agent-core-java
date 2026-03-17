/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.base;

import com.openjiuwen.core.workflow.Workflow;

import java.util.function.Supplier;

/**
 * Provider functional interface for creating Workflow instances.
 * Mirrors Python's {@code WorkflowProvider = Callable[..., Workflow]}.
 */
@FunctionalInterface
public interface WorkflowProvider extends Supplier<Workflow> {
}
