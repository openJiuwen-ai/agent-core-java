/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.Workflow;

/**
 * Sub-workflow component view exposing the wrapped workflow and its internal drawable.
 *
 * <p>Mirrors Python's {@code SubWorkflowComponent} in
 * {@code openjiuwen/core/workflow/components/flow/workflow_comp.py}.</p>
 */
public interface SubWorkflowComponent extends ComponentComposable {

    /**
     * Gets Python's {@code sub_workflow} property.
     *
     * @return wrapped workflow
     */
    Workflow getSubWorkflow();

    /**
     * Gets Python's {@code sub_workflow._internal} drawable owner.
     *
     * @return drawable-owning internal workflow
     */
    HasDrawable getSubWorkflowInternal();

    /**
     * Returns whether stream output caching is enabled.
     *
     * @return true when stream output is cached
     */
    boolean isCacheStream();
}
