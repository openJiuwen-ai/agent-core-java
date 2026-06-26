/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Mirrors Python's {@code VariableResult} in
 * {@code openjiuwen/core/memory/process/extract/memory_analyzer.py}.
 */
public class VariableResult {

    @JsonProperty("variable_key")
    private String variableKey = "";

    @JsonProperty("variable_value")
    private String variableValue = "";

    public VariableResult() {
    }

    public VariableResult(String variableKey, String variableValue) {
        this.variableKey = variableKey == null ? "" : variableKey;
        this.variableValue = variableValue == null ? "" : variableValue;
    }

    public String getVariableKey() {
        return variableKey;
    }

    public void setVariableKey(String variableKey) {
        this.variableKey = variableKey == null ? "" : variableKey;
    }

    public String getVariableValue() {
        return variableValue;
    }

    public void setVariableValue(String variableValue) {
        this.variableValue = variableValue == null ? "" : variableValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VariableResult that)) {
            return false;
        }
        return Objects.equals(variableKey, that.variableKey)
                && Objects.equals(variableValue, that.variableValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableKey, variableValue);
    }
}
