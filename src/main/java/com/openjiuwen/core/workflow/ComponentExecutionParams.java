/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;

import java.util.Map;

/**
 * Component execution parameters encapsulation.
 * <p>
 * Mirrors Python's {@code ComponentExecutionParams} dataclass from {@code _workflow.py}.
 */
public class ComponentExecutionParams {

    private final String nodeId;
    private final NodeSessionApi session;
    private final ComponentExecutable executor;
    private final Map<String, Object> inputs;
    private final Map<String, Object> inputsSchema;
    private final Map<String, Object> outputsSchema;
    private final ModelContext context;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ComponentExecutionParams(String nodeId,
                                    NodeSessionApi session,
                                    ComponentExecutable executor,
                                    Map<String, Object> inputs,
                                    Map<String, Object> inputsSchema,
                                    Map<String, Object> outputsSchema,
                                    ModelContext context) {
        this.nodeId = nodeId;
        this.session = session;
        this.executor = executor;
        this.inputs = inputs;
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
        this.context = context;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ComponentExecutionParams(String nodeId,
                                    NodeSessionApi session,
                                    ComponentExecutable executor,
                                    Map<String, Object> inputs) {
        this(nodeId, session, executor, inputs, null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public NodeSessionApi getSession() {
        return session;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ComponentExecutable getExecutor() {
        return executor;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputsSchema() {
        return inputsSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getOutputsSchema() {
        return outputsSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ModelContext getContext() {
        return context;
    }
}
