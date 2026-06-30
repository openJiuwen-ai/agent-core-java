/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

/**
 * Stub for I/O configuration of a workflow component.
 * <p>
 * Mirrors Python's IO config with inputs_schema and outputs_schema.
 */
public class IOConfig {

    private Object inputsSchema;
    private Object outputsSchema;

    /**
     * Auto-generated for codecheck compliance.
     */
    public IOConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public IOConfig(Object inputsSchema, Object outputsSchema) {
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getInputsSchema() {
        return inputsSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInputsSchema(Object inputsSchema) {
        this.inputsSchema = inputsSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getOutputsSchema() {
        return outputsSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOutputsSchema(Object outputsSchema) {
        this.outputsSchema = outputsSchema;
    }
}
