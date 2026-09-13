/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;

/**
 * Concrete sub-workflow component (alias for {@link SubWorkflowComponentImpl}).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.workflow_comp.SubWorkflowComponent}.
 * 
 * @since 0.1.7
 */
public class SubWorkflowComponent extends SubWorkflowComponentImpl {
    /**
     * SubWorkflowComponent.
     * 
     * @param subWorkflow subWorkflow
     * @since 0.1.7
     */
    public SubWorkflowComponent(Workflow subWorkflow) {
        super(subWorkflow);
    }
}
