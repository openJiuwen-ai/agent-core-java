/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.Workflow;

/**
 * Sub-workflow component implementation exposing Python's {@code sub_workflow}.
 *
 * <p>Mirrors Python's {@code SubWorkflowComponent} in
 * {@code openjiuwen/core/workflow/components/flow/workflow_comp.py}.</p>
 */
public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent {

    public static final String SUB_WORKFLOW_COMPONENT = "sub_workflow";

    private final Workflow subWorkflow;
    private final boolean cacheStream;
    private final SubWorkflowStreamState streamState = new SubWorkflowStreamState();

    public SubWorkflowComponentImpl(Workflow subWorkflow) {
        this(subWorkflow, false);
    }

    public SubWorkflowComponentImpl(Workflow subWorkflow, boolean cacheStream) {
        if (subWorkflow == null) {
            throw new IllegalArgumentException("sub_workflow is None");
        }
        this.subWorkflow = subWorkflow;
        this.cacheStream = cacheStream;
    }

    @Override
    public Workflow getSubWorkflow() {
        return subWorkflow;
    }

    @Override
    public HasDrawable getSubWorkflowInternal() {
        return subWorkflow.getInternalDrawable();
    }

    @Override
    public boolean isCacheStream() {
        return cacheStream;
    }

    public SubWorkflowStreamState getStreamState() {
        return streamState;
    }

    public String componentType() {
        return SUB_WORKFLOW_COMPONENT;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }
}
