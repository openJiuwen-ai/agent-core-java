/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.Iterator;
import java.util.Map;

/**
 * Component that wraps a sub-workflow and delegates execution to it.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.workflow_comp.SubWorkflowComponent}.
 * 
 * @since 0.1.7
 */
public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent {
    private static final String SUB_WORKFLOW_COMPONENT = "sub_workflow";

    private final Workflow subWorkflow;

    /**
     * SubWorkflowComponentImpl.
     * 
     * @param subWorkflow subWorkflow
     * @since 0.1.7
     */
    public SubWorkflowComponentImpl(Workflow subWorkflow) {
        if (subWorkflow == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_SUB_WORKFLOW_PARAM_INVALID, "error_msg",
                    "sub_workflow is None");
        }
        this.subWorkflow = subWorkflow;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : Map.of();
        return subWorkflow.invokeSubWorkflow(inputsMap.get(Constant.INPUTS_KEY), session, context,
                inputsMap.get(Constant.CONFIG_KEY));
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : Map.of();
        return (Iterator<Object>) (Iterator<?>) subWorkflow.streamSubWorkflow(inputsMap.get(Constant.INPUTS_KEY),
                session, context, inputsMap.get(Constant.CONFIG_KEY));
    }

    /**
     * graphInvoker.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean graphInvoker() {
        return true;
    }

    /**
     * componentType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String componentType() {
        return SUB_WORKFLOW_COMPONENT;
    }

    /**
     * getSubWorkflow.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Workflow getSubWorkflow() {
        return subWorkflow;
    }

    /**
     * getSubWorkflowInternal.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public HasDrawable getSubWorkflowInternal() {
        // The Drawable accesses workflow._internal which is the BaseWorkflow
        // This is kept as HasDrawable interface — Workflow would need to expose internal
        return subWorkflow.getInternalDrawable();
    }
}
