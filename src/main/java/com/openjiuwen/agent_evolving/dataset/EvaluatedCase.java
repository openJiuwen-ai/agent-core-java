/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.dataset;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.dataset.case.EvaluatedCase.
 */
public class EvaluatedCase {

    private Case caseData;
    private Map<String, Object> answer;
    private double score;
    private String reason;
    private Map<String, Double> perMetric;

    public EvaluatedCase() {
        this(null, null, 0.0d, "", null);
    }

    public EvaluatedCase(Case caseData, Map<String, Object> answer) {
        this(caseData, answer, 0.0d, "", null);
    }

    public EvaluatedCase(Case caseData,
                         Map<String, Object> answer,
                         double score,
                         String reason) {
        this(caseData, answer, score, reason, null);
    }

    public EvaluatedCase(Case caseData,
                         Map<String, Object> answer,
                         double score,
                         String reason,
                         Map<String, Double> perMetric) {
        this.caseData = caseData;
        this.answer = answer;
        this.score = clampScore(score);
        this.reason = reason != null ? reason : "";
        this.perMetric = perMetric;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Case getCase() {
        return caseData;
    }

    public void setCase(Case caseData) {
        this.caseData = caseData;
    }

    public Case getCaseData() {
        return caseData;
    }

    public void setCaseData(Case caseData) {
        this.caseData = caseData;
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = clampScore(score);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason != null ? reason : "";
    }

    public Map<String, Double> getPerMetric() {
        return perMetric;
    }

    public void setPerMetric(Map<String, Double> perMetric) {
        this.perMetric = perMetric;
    }

    public Map<String, Object> getInputs() {
        return caseData != null ? caseData.getInputs() : null;
    }

    public Map<String, Object> getLabel() {
        return caseData != null ? caseData.getLabel() : null;
    }

    public List<ToolInfo> getTools() {
        return caseData != null ? caseData.getTools() : null;
    }

    public String getCaseId() {
        return caseData != null ? caseData.getCaseId() : null;
    }

    private static double clampScore(double score) {
        return Math.max(0.0d, Math.min(1.0d, score));
    }

    public static final class Builder {
        private Case caseData;
        private Map<String, Object> answer;
        private double score = 0.0d;
        private String reason = "";
        private Map<String, Double> perMetric;

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

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder perMetric(Map<String, Double> perMetric) {
            this.perMetric = perMetric;
            return this;
        }

        public EvaluatedCase build() {
            return new EvaluatedCase(caseData, answer, score, reason, perMetric);
        }
    }
}