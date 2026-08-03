/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mirrors Python's {@code MemoryAnalyzerResult} in
 * {@code openjiuwen/core/memory/process/extract/memory_analyzer.py}.
 */
public class MemoryAnalyzerResult {

    @JsonProperty("has_key_information")
    private boolean hasKeyInformation = false;

    private List<VariableResult> variables = new ArrayList<>();

    private String summary = "";

    public MemoryAnalyzerResult() {
    }

    public MemoryAnalyzerResult(boolean hasKeyInformation, List<VariableResult> variables, String summary) {
        this.hasKeyInformation = hasKeyInformation;
        setVariables(variables);
        this.summary = summary == null ? "" : summary;
    }

    public boolean isHasKeyInformation() {
        return hasKeyInformation;
    }

    public void setHasKeyInformation(boolean hasKeyInformation) {
        this.hasKeyInformation = hasKeyInformation;
    }

    public List<VariableResult> getVariables() {
        return variables;
    }

    public void setVariables(List<VariableResult> variables) {
        this.variables = variables == null ? new ArrayList<>() : new ArrayList<>(variables);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemoryAnalyzerResult that)) {
            return false;
        }
        return hasKeyInformation == that.hasKeyInformation
                && Objects.equals(variables, that.variables)
                && Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasKeyInformation, variables, summary);
    }
}
