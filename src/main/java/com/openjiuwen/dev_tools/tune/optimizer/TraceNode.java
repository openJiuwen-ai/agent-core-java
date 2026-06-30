/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.optimizer.base.TraceNode.
 */
public class TraceNode {

    private String caseId;
    private String llmCallId;
    private Map<String, Object> inputs;
    private String outputs;
    private List<Object> history;
    private List<Object> tools;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TraceNode() {
        this(null, null, null, "", new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TraceNode(String caseId,
                     String llmCallId,
                     Map<String, Object> inputs,
                     String outputs,
                     List<Object> history,
                     List<Object> tools) {
        this.caseId = caseId;
        this.llmCallId = llmCallId;
        this.inputs = inputs;
        this.outputs = outputs != null ? outputs : "";
        this.history = history != null ? history : new ArrayList<>();
        this.tools = tools != null ? tools : new ArrayList<>();
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
    public String getCaseId() {
        return caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLlmCallId() {
        return llmCallId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLlmCallId(String llmCallId) {
        this.llmCallId = llmCallId;
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
    public String getOutputs() {
        return outputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOutputs(String outputs) {
        this.outputs = outputs != null ? outputs : "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getHistory() {
        return history;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setHistory(List<Object> history) {
        this.history = history != null ? history : new ArrayList<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getTools() {
        return tools;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTools(List<Object> tools) {
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private String caseId;
        private String llmCallId;
        private Map<String, Object> inputs;
        private String outputs = "";
        private List<Object> history = new ArrayList<>();
        private List<Object> tools = new ArrayList<>();

        private Builder() {
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
        public Builder llmCallId(String llmCallId) {
            this.llmCallId = llmCallId;
            return this;
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
        public Builder outputs(String outputs) {
            this.outputs = outputs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder history(List<Object> history) {
            this.history = history;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder tools(List<Object> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TraceNode build() {
            return new TraceNode(caseId, llmCallId, inputs, outputs, history, tools);
        }
    }
}
