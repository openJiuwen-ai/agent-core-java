/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.prompt.assemble.variables;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for prompt template variables.
 * <p>
 * Mirrors Python's {@code Variable} ABC.
 */
public abstract class Variable {

    protected String name;
    protected List<String> inputKeys;
    protected Object value = "";

    protected Variable(String name, List<String> inputKeys) {
        this.name = name;
        this.inputKeys = inputKeys != null ? inputKeys : List.of();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getInputKeys() {
        return inputKeys;
    }

    public Object getValue() {
        return value;
    }

    /**
     * Update the variable value based on the given arguments.
     *
     * @param kwargs key-value arguments
     */
    public abstract void update(Map<String, Object> kwargs);

    /**
     * Validate input, update {@code value}, and return it.
     *
     * @param kwargs key-value pairs for evaluation
     * @return updated value
     */
    public Object eval(Map<String, Object> kwargs) {
        Map<String, Object> inputKwargs = prepareInputs(kwargs);
        update(inputKwargs);
        return value;
    }

    /**
     * Filter kwargs to only include keys that are in {@code inputKeys}.
     */
    protected Map<String, Object> prepareInputs(Map<String, Object> kwargs) {
        return kwargs.entrySet().stream()
                .filter(e -> inputKeys.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
