/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.agent_evolving.dataset;

import java.util.Map;

/**
 * Evaluated sample with model output and score.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.dataset.case.EvaluatedCase}.</p>
 */
public class EvaluatedCase {

    /** Original Case. */
    private final Case aCase;

    /** Model output/prediction. */
    private final Map<String, Object> answer;

    /** Composite score in range [0, 1]. */
    private final double score;

    /** Reasoning for the score or error analysis. */
    private final String reason;

    /**
     * Construct an EvaluatedCase with case and answer.
     *
     * @param aCase  original case
     * @param answer model output/prediction
     */
    public EvaluatedCase(Case aCase, Map<String, Object> answer) {
        this(aCase, answer, 0.0, "");
    }

    /**
     * Construct an EvaluatedCase with all fields.
     *
     * @param aCase  original case
     * @param answer model output/prediction
     * @param score  composite score in [0, 1]
     * @param reason reasoning for the score
     */
    public EvaluatedCase(Case aCase, Map<String, Object> answer, double score, String reason) {
        if (aCase == null) {
            throw new IllegalArgumentException("case must not be null");
        }
        this.aCase = aCase;
        this.answer = answer;
        this.score = Math.max(0.0, Math.min(1.0, score));
        this.reason = reason != null ? reason : "";
    }

    public Case getCase() {
        return aCase;
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public double getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    /** Convenience delegate: get inputs from the case. */
    public Map<String, Object> getInputs() {
        return aCase.getInputs();
    }

    /** Convenience delegate: get label from the case. */
    public Map<String, Object> getLabel() {
        return aCase.getLabel();
    }

    /** Convenience delegate: get caseId from the case. */
    public String getCaseId() {
        return aCase.getCaseId();
    }

    @Override
    public String toString() {
        return "EvaluatedCase{case=" + aCase + ", answer=" + answer
                + ", score=" + score + ", reason='" + reason + "'}";
    }
}
