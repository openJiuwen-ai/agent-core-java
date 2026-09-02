/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

/**
 * Stub for I/O configuration of a workflow component.
 * <p>
 * Mirrors Python's IO config with inputs_schema and outputs_schema.
 * 
 * @since 0.1.7
 */
public class IOConfig {
    private Object inputsSchema;
    private Object outputsSchema;

    /**
     * IOConfig.
     * 
     * @since 0.1.7
     */
    public IOConfig() {
    }

    /**
     * IOConfig.
     * 
     * @param inputsSchema inputsSchema
     * @param outputsSchema outputsSchema
     * @since 0.1.7
     */
    public IOConfig(Object inputsSchema, Object outputsSchema) {
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
    }

    /**
     * getInputsSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getInputsSchema() {
        return inputsSchema;
    }

    /**
     * setInputsSchema.
     * 
     * @param inputsSchema inputsSchema
     * @since 0.1.7
     */
    public void setInputsSchema(Object inputsSchema) {
        this.inputsSchema = inputsSchema;
    }

    /**
     * getOutputsSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getOutputsSchema() {
        return outputsSchema;
    }

    /**
     * setOutputsSchema.
     * 
     * @param outputsSchema outputsSchema
     * @since 0.1.7
     */
    public void setOutputsSchema(Object outputsSchema) {
        this.outputsSchema = outputsSchema;
    }
}
