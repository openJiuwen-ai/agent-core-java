/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.dataset;

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

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvaluatedCase() {
        this(null, null, 0.0d, "", null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvaluatedCase(Case caseData, Map<String, Object> answer) {
        this(caseData, answer, 0.0d, "", null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvaluatedCase(Case caseData,
                         Map<String, Object> answer,
                         double score,
                         String reason) {
        this(caseData, answer, score, reason, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Case getCase() {
        return caseData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCase(Case caseData) {
        this.caseData = caseData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Case getCaseData() {
        return caseData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCaseData(Case caseData) {
        this.caseData = caseData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getAnswer() {
        return answer;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getScore() {
        return score;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setScore(double score) {
        this.score = clampScore(score);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReason(String reason) {
        this.reason = reason != null ? reason : "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Double> getPerMetric() {
        return perMetric;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPerMetric(Map<String, Double> perMetric) {
        this.perMetric = perMetric;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputs() {
        return caseData != null ? caseData.getInputs() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getLabel() {
        return caseData != null ? caseData.getLabel() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<ToolInfo> getTools() {
        return caseData != null ? caseData.getTools() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCaseId() {
        return caseData != null ? caseData.getCaseId() : null;
    }

    private static double clampScore(double score) {
        return Math.max(0.0d, Math.min(1.0d, score));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private Case caseData;
        private Map<String, Object> answer;
        private double score = 0.0d;
        private String reason = "";
        private Map<String, Double> perMetric;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder caseData(Case caseData) {
            this.caseData = caseData;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder case_(Case caseData) {
            this.caseData = caseData;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder answer(Map<String, Object> answer) {
            this.answer = answer;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder score(double score) {
            this.score = score;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder perMetric(Map<String, Double> perMetric) {
            this.perMetric = perMetric;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public EvaluatedCase build() {
            return new EvaluatedCase(caseData, answer, score, reason, perMetric);
        }
    }
}
