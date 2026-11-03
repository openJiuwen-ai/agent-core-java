/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;

/**
 * Public package alias for the sub-workflow component implementation.
 *
 * <p>Mirrors Python's {@code SubWorkflowComponent} in
 * {@code openjiuwen/core/workflow/components/flow/workflow_comp.py}.</p>
 */
public class SubWorkflowComponent extends SubWorkflowComponentImpl {

    public SubWorkflowComponent(Workflow subWorkflow) {
        super(subWorkflow);
    }

    public SubWorkflowComponent(Workflow subWorkflow, boolean cacheStream) {
        super(subWorkflow, cacheStream);
    }
}
