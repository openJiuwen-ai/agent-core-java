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

    public TraceNode() {
        this(null, null, null, "", new ArrayList<>(), new ArrayList<>());
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getLlmCallId() {
        return llmCallId;
    }

    public void setLlmCallId(String llmCallId) {
        this.llmCallId = llmCallId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public String getOutputs() {
        return outputs;
    }

    public void setOutputs(String outputs) {
        this.outputs = outputs != null ? outputs : "";
    }

    public List<Object> getHistory() {
        return history;
    }

    public void setHistory(List<Object> history) {
        this.history = history != null ? history : new ArrayList<>();
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(List<Object> tools) {
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    public static final class Builder {
        private String caseId;
        private String llmCallId;
        private Map<String, Object> inputs;
        private String outputs = "";
        private List<Object> history = new ArrayList<>();
        private List<Object> tools = new ArrayList<>();

        private Builder() {
        }

        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        public Builder llmCallId(String llmCallId) {
            this.llmCallId = llmCallId;
            return this;
        }

        public Builder inputs(Map<String, Object> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder outputs(String outputs) {
            this.outputs = outputs;
            return this;
        }

        public Builder history(List<Object> history) {
            this.history = history;
            return this;
        }

        public Builder tools(List<Object> tools) {
            this.tools = tools;
            return this;
        }

        public TraceNode build() {
            return new TraceNode(caseId, llmCallId, inputs, outputs, history, tools);
        }
    }
}