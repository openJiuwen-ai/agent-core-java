/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Base workflow component surface for graph construction components.
 *
 * <p>Mirrors Python's {@code WorkflowComponent} in
 * {@code openjiuwen/core/workflow/components/component.py}.</p>
 */
public abstract class WorkflowComponent implements ComponentComposable {

    public boolean graphInvoker() {
        return false;
    }

    public boolean skipTrace() {
        return false;
    }
}
