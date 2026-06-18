/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Mirrors Python's {@code WorkflowComponent} in
 * {@code openjiuwen/core/workflow/components/component.py}.
 *
 * @param <I> component input type
 * @param <O> component output type
 */
public abstract class WorkflowComponent<I, O> extends ComponentExecutable<I, O> implements ComponentComposable {
}
