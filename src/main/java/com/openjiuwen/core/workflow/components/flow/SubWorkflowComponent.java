// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.workflow.components.flow;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;

/**
 * Concrete sub-workflow component (alias for {@link SubWorkflowComponentImpl}).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.workflow_comp.SubWorkflowComponent}.
 */
public class SubWorkflowComponent extends SubWorkflowComponentImpl {

    public SubWorkflowComponent(Workflow subWorkflow) {
        super(subWorkflow);
    }
}
