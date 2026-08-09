/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;

/**
 * Component execution parameters encapsulation.
 * <p>
 * Mirrors Python's {@code execute_single_component} parameter set in
 * {@code openjiuwen/core/workflow/_workflow.py}.
 */
public class ComponentExecutionParams {

    private final String nodeId;
    private final BaseSession session;
    private final ComponentExecutable executor;
    private final Object inputs;
    private final Object inputsSchema;
    private final Object outputsSchema;
    private final ModelContext context;

    public ComponentExecutionParams(String nodeId,
                                    BaseSession session,
                                    ComponentExecutable executor,
                                    Object inputs,
                                    Object inputsSchema,
                                    Object outputsSchema,
                                    ModelContext context) {
        this.nodeId = nodeId;
        this.session = session;
        this.executor = executor;
        this.inputs = inputs;
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
        this.context = context;
    }

    public ComponentExecutionParams(String nodeId,
                                    BaseSession session,
                                    ComponentExecutable executor,
                                    Object inputs) {
        this(nodeId, session, executor, inputs, null, null, null);
    }

    public String getNodeId() {
        return nodeId;
    }

    public BaseSession getSession() {
        return session;
    }

    public ComponentExecutable getExecutor() {
        return executor;
    }

    public Object getInputs() {
        return inputs;
    }

    public Object getInputsSchema() {
        return inputsSchema;
    }

    public Object getOutputsSchema() {
        return outputsSchema;
    }

    public ModelContext getContext() {
        return context;
    }
}
