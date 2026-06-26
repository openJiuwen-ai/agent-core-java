/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace record for one optimized LLM call.
 *
 * <p>Mirrors Python's {@code TraceNode} in
 * {@code openjiuwen/dev_tools/tune/optimizer/base.py}.</p>
 */
public class TraceNode {

    private String caseId;
    private String llmCallId;
    private Map<String, Object> inputs = new LinkedHashMap<>();
    private String outputs = "";
    private List<BaseMessage> history = new ArrayList<>();
    private List<Object> tools = new ArrayList<>();

    public TraceNode() {
    }

    public TraceNode(String caseId, String llmCallId, Map<String, ?> inputs, String outputs) {
        this.caseId = caseId;
        this.llmCallId = llmCallId;
        setInputs(inputs);
        this.outputs = outputs == null ? "" : outputs;
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

    public void setInputs(Map<String, ?> inputs) {
        this.inputs = new LinkedHashMap<>();
        if (inputs != null) {
            inputs.forEach((key, value) -> this.inputs.put(key, value));
        }
    }

    public String getOutputs() {
        return outputs;
    }

    public void setOutputs(String outputs) {
        this.outputs = outputs == null ? "" : outputs;
    }

    public List<BaseMessage> getHistory() {
        return history;
    }

    public void setHistory(List<BaseMessage> history) {
        this.history = history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(List<?> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }
}
