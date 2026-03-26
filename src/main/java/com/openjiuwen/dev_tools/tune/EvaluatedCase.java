// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.tune;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.tune.base.EvaluatedCase}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatedCase {

    private Case case_;

    private Map<String, Object> answer;

    @Builder.Default
    private float score = 0.0f;

    @Builder.Default
    private String reason = "";

    public EvaluatedCase(Case caseData, Map<String, Object> answer) {
        this.case_ = caseData;
        this.answer = answer;
        this.score = 0.0f;
        this.reason = "";
    }

    public Map<String, Object> getInputs() {
        return case_ != null ? case_.getInputs() : null;
    }

    public Map<String, Object> getLabel() {
        return case_ != null ? case_.getLabel() : null;
    }

    public java.util.List<com.openjiuwen.core.foundation.tool.schema.ToolInfo> getTools() {
        return case_ != null ? case_.getTools() : null;
    }

    public String getCaseId() {
        return case_ != null ? case_.getCaseId() : null;
    }

    public Case getCase() {
        return case_;
    }

    public void setCase(Case caseData) {
        this.case_ = caseData;
    }
}