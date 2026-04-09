/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.base.Case.
 */
public class Case {

    private Map<String, Object> inputs;
    private Map<String, Object> label;
    private List<ToolInfo> tools;
    private String caseId;

    public Case() {
        this.caseId = newCaseId();
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label) {
        this(inputs, label, null, null);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools) {
        this(inputs, label, tools, null);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools, String caseId) {
        this.inputs = inputs;
        this.label = label;
        this.tools = tools;
        this.caseId = caseId != null ? caseId : newCaseId();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public Map<String, Object> getLabel() {
        return label;
    }

    public void setLabel(Map<String, Object> label) {
        this.label = label;
    }

    public List<ToolInfo> getTools() {
        return tools;
    }

    public void setTools(List<ToolInfo> tools) {
        this.tools = tools;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId != null ? caseId : newCaseId();
    }

    private static String newCaseId() {
        return UUID.randomUUID().toString();
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
            return new Case(inputs, label, tools, caseId);
        }
    }
}