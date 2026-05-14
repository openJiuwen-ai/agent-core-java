/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of memory analysis containing key information flag, variables, and summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryAnalyzerResult {
    @Builder.Default
    private boolean hasKeyInformation = false;
    @Builder.Default
    private List<VariableResult> variables = new ArrayList<>();
    @Builder.Default
    private String summary = "";

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
        this.variables = variables;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
