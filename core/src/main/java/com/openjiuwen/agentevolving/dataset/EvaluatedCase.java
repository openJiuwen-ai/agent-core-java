/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.dataset;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluated sample with model output and score.
 *
 * <p>Mirrors Python's {@code EvaluatedCase} in
 * {@code openjiuwen/agent_evolving/dataset/case.py}.</p>
 */
public class EvaluatedCase {

    private Case caseValue;
    private Map<String, Object> answer;
    private double score;
    private String reason;

    @JsonProperty("per_metric")
    private Map<String, Double> perMetric;

    public EvaluatedCase(Case caseValue) {
        this(caseValue, null, 0.0d, "", null);
    }

    public EvaluatedCase(
            Case caseValue,
            Map<String, Object> answer,
            double score,
            String reason,
            Map<String, Double> perMetric
    ) {
        setCase(caseValue);
        this.answer = answer == null ? null : new LinkedHashMap<>(answer);
        this.score = clampScore(score);
        this.reason = reason == null ? "" : reason;
        this.perMetric = perMetric == null ? null : new LinkedHashMap<>(perMetric);
    }

    public Case getCase() {
        return caseValue;
    }

    public void setCase(Case caseValue) {
        if (caseValue == null) {
            throw new IllegalArgumentException("case is required");
        }
        this.caseValue = caseValue;
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
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public Map<String, Double> getPerMetric() {
        return perMetric;
    }

    public void setPerMetric(Map<String, Double> perMetric) {
        this.perMetric = perMetric == null ? null : new LinkedHashMap<>(perMetric);
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

    public String getCaseId() {
        return caseValue.getCaseId();
    }

    public Map<String, Object> modelDump() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("case", caseValue.modelDump());
        data.put("answer", answer);
        data.put("score", score);
        data.put("reason", reason);
        data.put("per_metric", perMetric);
        return data;
    }

    private static double clampScore(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
