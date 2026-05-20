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

    /**
     * Auto-generated for codecheck compliance.
     */
    public Case() {
        this.caseId = newCaseId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label) {
        this(inputs, label, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools) {
        this(inputs, label, tools, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools, String caseId) {
        this.inputs = inputs;
        this.label = label;
        this.tools = tools;
        this.caseId = caseId != null ? caseId : newCaseId();
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
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getLabel() {
        return label;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLabel(Map<String, Object> label) {
        this.label = label;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<ToolInfo> getTools() {
        return tools;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTools(List<ToolInfo> tools) {
        this.tools = tools;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCaseId(String caseId) {
        this.caseId = caseId != null ? caseId : newCaseId();
    }

    private static String newCaseId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private Map<String, Object> inputs;
        private Map<String, Object> label;
        private List<ToolInfo> tools;
        private String caseId;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder inputs(Map<String, Object> inputs) {
            this.inputs = inputs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder label(Map<String, Object> label) {
            this.label = label;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder tools(List<ToolInfo> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Case build() {
            return new Case(inputs, label, tools, caseId);
        }
    }
}
