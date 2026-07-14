/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Definition of a tuning case.
 *
 * <p>Mirrors Python's {@code Case} in
 * {@code openjiuwen/dev_tools/tune/base.py}.</p>
 */
public class Case {
    private static final String DEFAULT_CASE_ID = UUID.randomUUID().toString();

    private Map<String, Object> inputs;
    private Map<String, Object> label;
    private List<ToolInfo> tools;

    @JsonProperty("case_id")
    private String caseId;

    public Case() {
        this.inputs = null;
        this.label = null;
        this.tools = null;
        this.caseId = DEFAULT_CASE_ID;
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label) {
        this(inputs, label, null, DEFAULT_CASE_ID);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools) {
        this(inputs, label, tools, DEFAULT_CASE_ID);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools, String caseId) {
        setInputs(inputs);
        setLabel(label);
        setTools(tools);
        this.caseId = caseId == null ? DEFAULT_CASE_ID : caseId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String defaultCaseId() {
        return DEFAULT_CASE_ID;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = requireNonEmpty(inputs, "inputs");
    }

    public Map<String, Object> getLabel() {
        return label;
    }

    public void setLabel(Map<String, Object> label) {
        this.label = requireNonEmpty(label, "label");
    }

    public List<ToolInfo> getTools() {
        return tools;
    }

    public void setTools(List<ToolInfo> tools) {
        this.tools = tools == null ? null : new ArrayList<>(tools);
    }

    @JsonProperty("case_id")
    public String getCaseId() {
        return caseId;
    }

    @JsonProperty("case_id")
    public void setCaseId(String caseId) {
        this.caseId = caseId == null ? DEFAULT_CASE_ID : caseId;
    }

    private static Map<String, Object> requireNonEmpty(Map<String, Object> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must contain at least one item");
        }
        return new LinkedHashMap<>(value);
    }

    public static final class Builder {
        private Map<String, Object> inputs;
        private Map<String, Object> label;
        private List<ToolInfo> tools;
        private String caseId;

        private Builder() {
        }

        public Builder inputs(Map<String, Object> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder label(Map<String, Object> label) {
            this.label = label;
            return this;
        }

        public Builder tools(List<ToolInfo> tools) {
            this.tools = tools;
            return this;
        }

        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        public Case build() {
            if (inputs == null && label == null && tools == null && caseId == null) {
                return new Case();
            }
            return new Case(inputs, label, tools, caseId);
        }
    }
}
