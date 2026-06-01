/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.List;
import java.util.Map;

/**
 * Named parameters for adding a nested property to an {@link OutputsField}.
 * <p>
 * Mirrors Python's {@code OutputPropertySpec} dataclass.
 */
public class OutputPropertySpec {
    private final List<String> variableNames;
    private final String description;
    private final int index;
    private final String varType;
    private final Map<String, Object> items;
    private final Map<String, Object> properties;
    private final List<String> required;

    public OutputPropertySpec(List<String> variableNames, String description, int index, String varType) {
        this(variableNames, description, index, varType, null, null, null);
    }

    public OutputPropertySpec(List<String> variableNames,
                              String description,
                              int index,
                              String varType,
                              Map<String, Object> items,
                              Map<String, Object> properties,
                              List<String> required) {
        this.variableNames = variableNames;
        this.description = description;
        this.index = index;
        this.varType = varType;
        this.items = items;
        this.properties = properties;
        this.required = required;
    }

    public OutputPropertySpec withVariableNames(List<String> names) {
        return new OutputPropertySpec(names, description, index, varType, items, properties, required);
    }

    public List<String> getVariableNames() {
        return variableNames;
    }

    public String getDescription() {
        return description;
    }

    public int getIndex() {
        return index;
    }

    public String getVarType() {
        return varType;
    }

    public Map<String, Object> getItems() {
        return items;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }
}
