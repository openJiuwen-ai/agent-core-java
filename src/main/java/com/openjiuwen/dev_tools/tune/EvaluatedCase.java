/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.base.EvaluatedCase.
 */
public class EvaluatedCase {

    private Case caseData;
    private Map<String, Object> answer;
    private float score;
    private String reason;

    public EvaluatedCase() {
        this(null, null, 0.0f, "");
    }

    public EvaluatedCase(Case caseData, Map<String, Object> answer) {
        this(caseData, answer, 0.0f, "");
    }

    public EvaluatedCase(Case caseData, Map<String, Object> answer, float score, String reason) {
        this.caseData = caseData;
        this.answer = answer;
        this.score = clampScore(score);
        this.reason = reason != null ? reason : "";
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

    public Case getCase_() {
        return caseData;
    }

    public void setCase_(Case caseData) {
        this.caseData = caseData;
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = clampScore(score);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason != null ? reason : "";
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

    private static float clampScore(float score) {
        return Math.max(0.0f, Math.min(1.0f, score));
    }

    public static final class Builder {
        private Case caseData;
        private Map<String, Object> answer;
        private float score = 0.0f;
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

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public EvaluatedCase build() {
            return new EvaluatedCase(caseData, answer, score, reason);
        }
    }
}