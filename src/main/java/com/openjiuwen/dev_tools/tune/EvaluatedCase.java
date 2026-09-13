/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.base.EvaluatedCase.
 * 
 * @since 0.1.7
 */
public class EvaluatedCase {
    private Case caseData;
    private Map<String, Object> answer;
    private float score;
    private String reason;

    /**
     * EvaluatedCase.
     * 
     * @since 0.1.7
     */
    public EvaluatedCase() {
        this(null, null, 0.0f, "");
    }

    /**
     * EvaluatedCase.
     * 
     * @param caseData caseData
     * @param answer answer
     * @since 0.1.7
     */
    public EvaluatedCase(Case caseData, Map<String, Object> answer) {
        this(caseData, answer, 0.0f, "");
    }

    /**
     * EvaluatedCase.
     * 
     * @param caseData caseData
     * @param answer answer
     * @param score score
     * @param reason reason
     * @since 0.1.7
     */
    public EvaluatedCase(Case caseData, Map<String, Object> answer, float score, String reason) {
        this.caseData = caseData;
        this.answer = answer;
        this.score = clampScore(score);
        this.reason = reason != null ? reason : "";
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * getCase.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Case getCase() {
        return caseData;
    }

    /**
     * setCase.
     * 
     * @param caseData caseData
     * @since 0.1.7
     */
    public void setCase(Case caseData) {
        this.caseData = caseData;
    }

    /**
     * getCaseData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Case getCaseData() {
        return caseData;
    }

    /**
     * setCaseData.
     * 
     * @param caseData caseData
     * @since 0.1.7
     */
    public void setCaseData(Case caseData) {
        this.caseData = caseData;
    }

    /**
     * getCase_.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Case getCase_() {
        return caseData;
    }

    /**
     * setCase_.
     * 
     * @param caseData caseData
     * @since 0.1.7
     */
    public void setCase_(Case caseData) {
        this.caseData = caseData;
    }

    /**
     * getAnswer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getAnswer() {
        return answer;
    }

    /**
     * setAnswer.
     * 
     * @param answer answer
     * @since 0.1.7
     */
    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    /**
     * getScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public float getScore() {
        return score;
    }

    /**
     * setScore.
     * 
     * @param score score
     * @since 0.1.7
     */
    public void setScore(float score) {
        this.score = clampScore(score);
    }

    /**
     * getReason.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReason() {
        return reason;
    }

    /**
     * setReason.
     * 
     * @param reason reason
     * @since 0.1.7
     */
    public void setReason(String reason) {
        this.reason = reason != null ? reason : "";
    }

    /**
     * getInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInputs() {
        return caseData != null ? caseData.getInputs() : null;
    }

    /**
     * getLabel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLabel() {
        return caseData != null ? caseData.getLabel() : null;
    }

    /**
     * getTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ToolInfo> getTools() {
        return caseData != null ? caseData.getTools() : null;
    }

    /**
     * getCaseId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCaseId() {
        return caseData != null ? caseData.getCaseId() : null;
    }

    /**
     * clampScore.
     * 
     * @param score score
     * @return the result
     * @since 0.1.7
     */
    private static float clampScore(float score) {
        return Math.max(0.0f, Math.min(1.0f, score));
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private Case caseData;
        private Map<String, Object> answer;
        private float score = 0.0f;
        private String reason = "";

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * caseData.
         * 
         * @param caseData caseData
         * @return the result
         * @since 0.1.7
         */
        public Builder caseData(Case caseData) {
            this.caseData = caseData;
            return this;
        }

        /**
         * case_.
         * 
         * @param caseData caseData
         * @return the result
         * @since 0.1.7
         */
        public Builder case_(Case caseData) {
            this.caseData = caseData;
            return this;
        }

        /**
         * answer.
         * 
         * @param answer answer
         * @return the result
         * @since 0.1.7
         */
        public Builder answer(Map<String, Object> answer) {
            this.answer = answer;
            return this;
        }

        /**
         * score.
         * 
         * @param score score
         * @return the result
         * @since 0.1.7
         */
        public Builder score(float score) {
            this.score = score;
            return this;
        }

        /**
         * reason.
         * 
         * @param reason reason
         * @return the result
         * @since 0.1.7
         */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public EvaluatedCase build() {
            return new EvaluatedCase(caseData, answer, score, reason);
        }
    }
}
