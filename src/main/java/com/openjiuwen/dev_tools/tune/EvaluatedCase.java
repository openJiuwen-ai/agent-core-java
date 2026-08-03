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

    public EvaluatedCase() {
        this.caseValue = null;
        this.answer = null;
        this.score = 0.0d;
        this.reason = "";
    }

    public EvaluatedCase(Case caseValue) {
        this(caseValue, null, 0.0d, "");
    }

    public EvaluatedCase(Case caseValue, Map<String, Object> answer) {
        this(caseValue, answer, 0.0d, "");
    }

    public EvaluatedCase(Case caseValue, Map<String, Object> answer, float score, String reason) {
        this(caseValue, answer, (double) score, reason);
    }

    public EvaluatedCase(Case caseValue, Map<String, Object> answer, double score, String reason) {
        this.caseValue = Objects.requireNonNull(caseValue, "case");
        setAnswer(answer);
        setScore(score);
        this.reason = reason == null ? "" : reason;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("case")
    public Case getCase() {
        return caseValue;
    }

    @JsonProperty("case")
    public void setCase(Case caseValue) {
        this.caseValue = Objects.requireNonNull(caseValue, "case");
    }

    public Case getCaseData() {
        return caseValue;
    }

    public void setCaseData(Case caseValue) {
        setCase(caseValue);
    }

    public Case getCase_() {
        return caseValue;
    }

    public void setCase_(Case caseValue) {
        setCase(caseValue);
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer == null ? null : new LinkedHashMap<>(answer);
    }

    public float getScore() {
        return (float) score;
    }

    public void setScore(double score) {
        if (score < 0.0d || score > 1.0d) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0");
        }
        this.score = score;
    }

    public void setScore(float score) {
        setScore((double) score);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public Map<String, Object> getInputs() {
        return caseValue == null ? null : caseValue.getInputs();
    }

    public Map<String, Object> getLabel() {
        return caseValue == null ? null : caseValue.getLabel();
    }

    public List<ToolInfo> getTools() {
        return caseValue == null ? null : caseValue.getTools();
    }

    @JsonProperty("case_id")
    public String getCaseId() {
        return caseValue == null ? null : caseValue.getCaseId();
    }

    public static final class Builder {
        private Case caseData;
        private Map<String, Object> answer;
        private double score;
        private String reason = "";

        private Builder() {
        }

        public Builder caseData(Case caseData) {
            this.caseData = caseData;
            return this;
        }

        public Builder case_(Case caseData) {
            this.caseData = caseData;
            return this;
        }

        public Builder answer(Map<String, Object> answer) {
            this.answer = answer;
            return this;
        }

        public Builder score(float score) {
            this.score = score;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public EvaluatedCase build() {
            if (caseData == null && answer == null && score == 0.0d && (reason == null || reason.isEmpty())) {
                return new EvaluatedCase();
            }
            return new EvaluatedCase(caseData, answer, score, reason);
        }
    }
}
