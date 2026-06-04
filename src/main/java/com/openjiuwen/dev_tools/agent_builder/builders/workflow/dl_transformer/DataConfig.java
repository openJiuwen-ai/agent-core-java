/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.List;
import java.util.Map;

/**
 * Data configuration model.
 * <p>
 * Mirrors Python's {@code DataConfig} dataclass.
 */
public class DataConfig {
    private String title;
    private InputsField inputs;
    private OutputsField outputs;
    private List<Map<String, Object>> branches;
    private Map<String, Object> exceptionConfig;

    public DataConfig() {
        this("");
    }

    public DataConfig(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public InputsField getInputs() {
        return inputs;
    }

    public void setInputs(InputsField inputs) {
        this.inputs = inputs;
    }

    public OutputsField getOutputs() {
        return outputs;
    }

    public void setOutputs(OutputsField outputs) {
        this.outputs = outputs;
    }

    public List<Map<String, Object>> getBranches() {
        return branches;
    }

    public void setBranches(List<Map<String, Object>> branches) {
        this.branches = branches;
    }

    public Map<String, Object> getExceptionConfig() {
        return exceptionConfig;
    }

    public void setExceptionConfig(Map<String, Object> exceptionConfig) {
        this.exceptionConfig = exceptionConfig;
    }
}
