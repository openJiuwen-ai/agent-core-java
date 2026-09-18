/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Input/output configuration for a component.
 * <p>
 * Mirrors Python's {@code CompIOConfig} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompIOConfig {

    @JsonProperty("inputs_schema")
    private SchemaOrTransformer inputsSchema;

    @JsonProperty("outputs_schema")
    private SchemaOrTransformer outputsSchema;

    public CompIOConfig() {
    }

    public CompIOConfig(SchemaOrTransformer inputsSchema, SchemaOrTransformer outputsSchema) {
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
    }

    public SchemaOrTransformer getInputsSchema() {
        return inputsSchema;
    }

    public void setInputsSchema(SchemaOrTransformer inputsSchema) {
        this.inputsSchema = inputsSchema;
    }

    public SchemaOrTransformer getOutputsSchema() {
        return outputsSchema;
    }

    public void setOutputsSchema(SchemaOrTransformer outputsSchema) {
        this.outputsSchema = outputsSchema;
    }
}
