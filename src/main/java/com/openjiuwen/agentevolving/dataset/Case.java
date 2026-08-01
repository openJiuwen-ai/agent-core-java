/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.dataset;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's openjiuwen.agent_evolving.dataset.case.Case.
 * 
 * @since 0.1.7
 */
public class Case {
    private Map<String, Object> inputs;
    private Map<String, Object> label;
    private List<ToolInfo> tools;
    private String caseId;

    /**
     * Case.
     * 
     * @since 0.1.7
     */
    public Case() {
        this.caseId = newCaseId();
    }

    /**
     * Case.
     * 
     * @param inputs inputs
     * @param label label
     * @since 0.1.7
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label) {
        this(inputs, label, null, null);
    }

    /**
     * Case.
     * 
     * @param inputs inputs
     * @param label label
     * @param caseId caseId
     * @since 0.1.7
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label, String caseId) {
        this(inputs, label, null, caseId);
    }

    /**
     * Case.
     * 
     * @param inputs inputs
     * @param label label
     * @param tools tools
     * @param caseId caseId
     * @since 0.1.7
     */
    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools, String caseId) {
        validateMap(inputs, "inputs");
        validateMap(label, "label");
        this.inputs = inputs;
        this.label = label;
        this.tools = tools;
        this.caseId = caseId != null ? caseId : newCaseId();
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
     * getInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /**
     * setInputs.
     * 
     * @param inputs inputs
     * @since 0.1.7
     */
    public void setInputs(Map<String, Object> inputs) {
        validateMap(inputs, "inputs");
        this.inputs = inputs;
    }

    /**
     * getLabel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLabel() {
        return label;
    }

    /**
     * setLabel.
     * 
     * @param label label
     * @since 0.1.7
     */
    public void setLabel(Map<String, Object> label) {
        validateMap(label, "label");
        this.label = label;
    }

    /**
     * getTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ToolInfo> getTools() {
        return tools;
    }

    /**
     * setTools.
     * 
     * @param tools tools
     * @since 0.1.7
     */
    public void setTools(List<ToolInfo> tools) {
        this.tools = tools;
    }

    /**
     * getCaseId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * setCaseId.
     * 
     * @param caseId caseId
     * @since 0.1.7
     */
    public void setCaseId(String caseId) {
        this.caseId = caseId != null ? caseId : newCaseId();
    }

    /**
     * newCaseId.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static String newCaseId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * validateMap.
     * 
     * @param data data
     * @param fieldName fieldName
     * @since 0.1.7
     */
    private static void validateMap(Map<String, Object> data, String fieldName) {
        if (data == null || data.isEmpty()) {
            throw new ValidationError(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    Map.of("error_msg", fieldName + " must contain at least one key"));
        }
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private Map<String, Object> inputs;
        private Map<String, Object> label;
        private List<ToolInfo> tools;
        private String caseId;

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * inputs.
         * 
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public Builder inputs(Map<String, Object> inputs) {
            this.inputs = inputs;
            return this;
        }

        /**
         * label.
         * 
         * @param label label
         * @return the result
         * @since 0.1.7
         */
        public Builder label(Map<String, Object> label) {
            this.label = label;
            return this;
        }

        /**
         * tools.
         * 
         * @param tools tools
         * @return the result
         * @since 0.1.7
         */
        public Builder tools(List<ToolInfo> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * caseId.
         * 
         * @param caseId caseId
         * @return the result
         * @since 0.1.7
         */
        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Case build() {
            return new Case(inputs, label, tools, caseId);
        }
    }
}
