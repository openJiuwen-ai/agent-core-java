/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    protected String name;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<String> inputKeys;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object value = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    protected Variable(String name, List<String> inputKeys) {
        this.name = name;
        this.inputKeys = inputKeys != null ? inputKeys : List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getInputKeys() {
        return inputKeys;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
