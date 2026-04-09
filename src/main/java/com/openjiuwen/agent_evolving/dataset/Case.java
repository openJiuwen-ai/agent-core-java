  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.agent_evolving.dataset;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's openjiuwen.agent_evolving.dataset.case.Case.
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

    public Case(Map<String, Object> inputs, Map<String, Object> label, String caseId) {
        this(inputs, label, null, caseId);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools, String caseId) {
        validateMap(inputs, "inputs");
        validateMap(label, "label");
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
        validateMap(inputs, "inputs");
        this.inputs = inputs;
    }

    public Map<String, Object> getLabel() {
        return label;
    }

    public void setLabel(Map<String, Object> label) {
        validateMap(label, "label");
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
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void validateMap(Map<String, Object> data, String fieldName) {
        if (data == null || data.isEmpty()) {
            throw new ValidationError(
                    StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    Map.of("error_msg", fieldName + " must contain at least one key")
            );
        }
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
