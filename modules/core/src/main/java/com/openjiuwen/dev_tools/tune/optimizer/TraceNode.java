/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.optimizer.base.TraceNode.
 * 
 * @since 0.1.7
 */
public class TraceNode {
    private String caseId;
    private String llmCallId;
    private Map<String, Object> inputs;
    private String outputs;
    private List<Object> history;
    private List<Object> tools;

    /**
     * TraceNode.
     * 
     * @since 0.1.7
     */
    public TraceNode() {
        this(null, null, null, "", new ArrayList<>(), new ArrayList<>());
    }

    /**
     * TraceNode.
     * 
     * @param caseId caseId
     * @param llmCallId llmCallId
     * @param inputs inputs
     * @param outputs outputs
     * @param history history
     * @param tools tools
     * @since 0.1.7
     */
    public TraceNode(String caseId, String llmCallId, Map<String, Object> inputs, String outputs, List<Object> history,
            List<Object> tools) {
        this.caseId = caseId;
        this.llmCallId = llmCallId;
        this.inputs = inputs;
        this.outputs = outputs != null ? outputs : "";
        this.history = history != null ? history : new ArrayList<>();
        this.tools = tools != null ? tools : new ArrayList<>();
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
        this.caseId = caseId;
    }

    /**
     * getLlmCallId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLlmCallId() {
        return llmCallId;
    }

    /**
     * setLlmCallId.
     * 
     * @param llmCallId llmCallId
     * @since 0.1.7
     */
    public void setLlmCallId(String llmCallId) {
        this.llmCallId = llmCallId;
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
        this.inputs = inputs;
    }

    /**
     * getOutputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOutputs() {
        return outputs;
    }

    /**
     * setOutputs.
     * 
     * @param outputs outputs
     * @since 0.1.7
     */
    public void setOutputs(String outputs) {
        this.outputs = outputs != null ? outputs : "";
    }

    /**
     * getHistory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getHistory() {
        return history;
    }

    /**
     * setHistory.
     * 
     * @param history history
     * @since 0.1.7
     */
    public void setHistory(List<Object> history) {
        this.history = history != null ? history : new ArrayList<>();
    }

    /**
     * getTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getTools() {
        return tools;
    }

    /**
     * setTools.
     * 
     * @param tools tools
     * @since 0.1.7
     */
    public void setTools(List<Object> tools) {
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private String caseId;
        private String llmCallId;
        private Map<String, Object> inputs;
        private String outputs = "";

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private List<Object> history = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private List<Object> tools = new ArrayList<>();

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
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
         * llmCallId.
         * 
         * @param llmCallId llmCallId
         * @return the result
         * @since 0.1.7
         */
        public Builder llmCallId(String llmCallId) {
            this.llmCallId = llmCallId;
            return this;
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
         * outputs.
         * 
         * @param outputs outputs
         * @return the result
         * @since 0.1.7
         */
        public Builder outputs(String outputs) {
            this.outputs = outputs;
            return this;
        }

        /**
         * history.
         * 
         * @param history history
         * @return the result
         * @since 0.1.7
         */
        public Builder history(List<Object> history) {
            this.history = history;
            return this;
        }

        /**
         * tools.
         * 
         * @param tools tools
         * @return the result
         * @since 0.1.7
         */
        public Builder tools(List<Object> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public TraceNode build() {
            return new TraceNode(caseId, llmCallId, inputs, outputs, history, tools);
        }
    }
}
