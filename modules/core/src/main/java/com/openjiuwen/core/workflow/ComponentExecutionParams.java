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
 * 
 * @since 0.1.7
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
     * ComponentExecutionParams.
     * 
     * @param nodeId nodeId
     * @param session session
     * @param executor executor
     * @param inputs inputs
     * @param inputsSchema inputsSchema
     * @param outputsSchema outputsSchema
     * @param context context
     * @since 0.1.7
     */
    public ComponentExecutionParams(String nodeId, NodeSessionApi session, ComponentExecutable executor,
            Map<String, Object> inputs, Map<String, Object> inputsSchema, Map<String, Object> outputsSchema,
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
     * ComponentExecutionParams.
     * 
     * @param nodeId nodeId
     * @param session session
     * @param executor executor
     * @param inputs inputs
     * @since 0.1.7
     */
    public ComponentExecutionParams(String nodeId, NodeSessionApi session, ComponentExecutable executor,
            Map<String, Object> inputs) {
        this(nodeId, session, executor, inputs, null, null, null);
    }

    /**
     * getNodeId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * getSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public NodeSessionApi getSession() {
        return session;
    }

    /**
     * getExecutor.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ComponentExecutable getExecutor() {
        return executor;
    }

    /**
     * getInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /**
     * getInputsSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInputsSchema() {
        return inputsSchema;
    }

    /**
     * getOutputsSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getOutputsSchema() {
        return outputsSchema;
    }

    /**
     * getContext.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelContext getContext() {
        return context;
    }
}
