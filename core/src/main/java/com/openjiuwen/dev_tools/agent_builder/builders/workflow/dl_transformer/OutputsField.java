/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outputs field model.
 * <p>
 * Mirrors Python's {@code OutputsField} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/models.py}.
 */
public class OutputsField {
    private String type;
    private Map<String, OutputsField> properties;
    private List<String> required;
    private String description;
    private String defaultValue;
    private Map<String, Integer> extra;
    private Map<String, Object> items;

    public OutputsField() {
        this("object");
    }

    public OutputsField(String type) {
        this.type = type;
    }

    public OutputsField(String type, String description) {
        this.type = type;
        this.description = description;
    }

    public void putProperty(String name, OutputsField property) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.put(name, property);
    }

    public void addProperty(OutputPropertySpec spec) {
        List<String> variableNames = spec.getVariableNames();
        if (variableNames == null || variableNames.isEmpty()) {
            return;
        }

        String key = variableNames.get(0);
        boolean leaf = variableNames.size() == 1;
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }

        if (!properties.containsKey(key)) {
            OutputsField child = new OutputsField();
            if (leaf) {
                child.type = spec.getVarType() != null ? spec.getVarType() : "string";
                child.description = spec.getDescription();
                child.extra = new LinkedHashMap<>();
                child.extra.put("index", spec.getIndex());
                child.items = spec.getItems() != null ? new LinkedHashMap<>(spec.getItems()) : null;
                if ("object".equals(spec.getVarType())) {
                    child.type = "object";
                    child.properties = copyProperties(spec.getProperties());
                    child.required = spec.getRequired() != null ? new ArrayList<>(spec.getRequired()) : new ArrayList<>();
                }
            } else {
                child.type = "object";
                child.properties = new LinkedHashMap<>();
                child.required = new ArrayList<>();
            }
            properties.put(key, child);
        }

        OutputsField outputField = properties.get(key);
        outputField.addProperty(spec.withVariableNames(variableNames.subList(1, variableNames.size())));
    }

    private static Map<String, OutputsField> copyProperties(Map<String, Object> raw) {
        Map<String, OutputsField> copied = new LinkedHashMap<>();
        if (raw == null) {
            return copied;
        }
        for (String key : raw.keySet()) {
            copied.put(key, new OutputsField());
        }
        return copied;
    }

    public String getType() {
        return type;
    }

    public Map<String, OutputsField> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required != null ? new ArrayList<>(required) : null;
    }

    public String getDescription() {
        return description;
    }

    public String getDefault() {
        return defaultValue;
    }

    public Map<String, Integer> getExtra() {
        return extra;
    }

    public Map<String, Object> getItems() {
        return items;
    }
}
