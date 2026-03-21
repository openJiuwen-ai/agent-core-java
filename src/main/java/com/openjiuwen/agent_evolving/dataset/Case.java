/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.agent_evolving.dataset;

import java.util.Map;
import java.util.UUID;

/**
 * Single training/evaluation sample.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.dataset.case.Case}.</p>
 */
public class Case {

    /** Input data (e.g., query or conversation content). */
    private final Map<String, Object> inputs;

    /** Expected answer or desired output. */
    private final Map<String, Object> label;

    /** Unique identifier for the sample. */
    private final String caseId;

    /**
     * Construct a Case with inputs and label.
     *
     * @param inputs input data map, must not be null or empty
     * @param label  expected output map, must not be null or empty
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be null or empty");
        }
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("label must not be null or empty");
        }
        this.inputs = inputs;
        this.label = label;
        this.caseId = UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Construct a Case with inputs, label, and explicit caseId.
     *
     * @param inputs input data map
     * @param label  expected output map
     * @param caseId unique identifier
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label, String caseId) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be null or empty");
        }
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("label must not be null or empty");
        }
        this.inputs = inputs;
        this.label = label;
        this.caseId = caseId != null ? caseId : UUID.randomUUID().toString().replace("-", "");
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public Map<String, Object> getLabel() {
        return label;
    }

    public String getCaseId() {
        return caseId;
    }

    @Override
    public String toString() {
        return "Case{inputs=" + inputs + ", label=" + label + ", caseId='" + caseId + "'}";
    }
}
