/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component;

/**
 * Stub for I/O configuration of a workflow component.
 * <p>
 * Mirrors Python's IO config with inputs_schema and outputs_schema.
 */
public class IOConfig {

    private Object inputsSchema;
    private Object outputsSchema;

    public IOConfig() {
    }

    public IOConfig(Object inputsSchema, Object outputsSchema) {
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
    }

    public Object getInputsSchema() {
        return inputsSchema;
    }

    public void setInputsSchema(Object inputsSchema) {
        this.inputsSchema = inputsSchema;
    }

    public Object getOutputsSchema() {
        return outputsSchema;
    }

    public void setOutputsSchema(Object outputsSchema) {
        this.outputsSchema = outputsSchema;
    }
}
