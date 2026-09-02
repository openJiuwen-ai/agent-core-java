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
 * 
 * @since 0.1.7
 */
public abstract class Variable {
    /**
     * name.
     * 
     * @since 0.1.7
     */
    protected String name;

    /**
     * inputKeys.
     * 
     * @since 0.1.7
     */
    protected List<String> inputKeys;

    /**
     * value.
     * 
     * @since 0.1.7
     */
    protected Object value = "";

    /**
     * Variable.
     * 
     * @param name name
     * @param inputKeys inputKeys
     * @since 0.1.7
     */
    protected Variable(String name, List<String> inputKeys) {
        this.name = name;
        this.inputKeys = inputKeys != null ? inputKeys : List.of();
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getInputKeys.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getInputKeys() {
        return inputKeys;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getValue() {
        return value;
    }

    /**
     * Update the variable value based on the given arguments.
     * 
     * @param kwargs key-value arguments
     * @since 0.1.7
     */
    public abstract void update(Map<String, Object> kwargs);

    /**
     * Validate input, update {@code value}, and return it.
     * 
     * @param kwargs key-value pairs for evaluation
     * @return updated value
     * @since 0.1.7
     */
    public Object eval(Map<String, Object> kwargs) {
        Map<String, Object> inputKwargs = prepareInputs(kwargs);
        update(inputKwargs);
        return value;
    }

    /**
     * Filter kwargs to only include keys that are in {@code inputKeys}.
     * 
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    protected Map<String, Object> prepareInputs(Map<String, Object> kwargs) {
        return kwargs.entrySet().stream().filter(e -> inputKeys.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
