/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single training/evaluation sample.
 *
 * <p>Mirrors Python's {@code Case} in
 * {@code openjiuwen/agent_evolving/dataset/case.py}.</p>
 */
public class Case {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private Map<String, Object> inputs;
    private Map<String, Object> label;
    private List<ToolInfo> tools;

    @JsonProperty("case_id")
    private String caseId;

    public Case(Map<String, Object> inputs, Map<String, Object> label) {
        this(inputs, label, null, null);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, String caseId) {
        this(inputs, label, null, caseId);
    }

    public Case(Map<String, Object> inputs, Map<String, Object> label, List<ToolInfo> tools, String caseId) {
        setInputs(inputs);
        setLabel(label);
        this.tools = tools;
        this.caseId = caseId == null ? newCaseId() : caseId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must have at least one key");
        }
        this.inputs = new LinkedHashMap<>(inputs);
    }

    public Map<String, Object> getLabel() {
        return label;
    }

    public void setLabel(Map<String, Object> label) {
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("label must have at least one key");
        }
        this.label = new LinkedHashMap<>(label);
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
        this.caseId = caseId;
    }

    public Map<String, Object> modelDump() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inputs", inputs);
        data.put("label", label);
        data.put("tools", tools);
        data.put("case_id", caseId);
        return data;
    }

    public String modelDumpJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(modelDump());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize Case", exception);
        }
    }

    private static String newCaseId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
