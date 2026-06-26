/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definition of an evaluated tuning case.
 *
 * <p>Mirrors Python's {@code EvaluatedCase} in
 * {@code openjiuwen/dev_tools/tune/base.py}.</p>
 */
public class EvaluatedCase {
    @JsonProperty("case")
    private Case caseValue;
    private Map<String, Object> answer;
    private double score;
    private String reason;

    public EvaluatedCase(Case caseValue) {
        this(caseValue, null, 0.0d, "");
    }

    public EvaluatedCase(Case caseValue, Map<String, Object> answer, double score, String reason) {
        this.caseValue = Objects.requireNonNull(caseValue, "case");
        setAnswer(answer);
        setScore(score);
        this.reason = reason == null ? "" : reason;
    }

    @JsonProperty("case")
    public Case getCase() {
        return caseValue;
    }

    @JsonProperty("case")
    public void setCase(Case caseValue) {
        this.caseValue = Objects.requireNonNull(caseValue, "case");
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer == null ? null : new LinkedHashMap<>(answer);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        if (score < 0.0d || score > 1.0d) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0");
        }
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public Map<String, Object> getInputs() {
        return caseValue.getInputs();
    }

    public Map<String, Object> getLabel() {
        return caseValue.getLabel();
    }

    public List<ToolInfo> getTools() {
        return caseValue.getTools();
    }

    @JsonProperty("case_id")
    public String getCaseId() {
        return caseValue.getCaseId();
    }
}
