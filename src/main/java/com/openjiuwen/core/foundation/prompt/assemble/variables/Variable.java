// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.prompt.assemble.variables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for variable.
 * <p>
 * Variables are used in prompt templates to represent placeholders that can be dynamically replaced.
 * Each variable has a name, a list of input keys it depends on, and a value.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/prompt/assemble/variables/variable.py</p>
 */
public abstract class Variable {
    private String name;
    private List<String> inputKeys;
    private String value;

    /**
     * Constructs a Variable with the specified name and input keys.
     *
     * @param name      the name of the variable
     * @param inputKeys the list of input keys this variable depends on (can be null)
     */
    public Variable(String name, List<String> inputKeys) {
        this.name = name;
        this.inputKeys = inputKeys;
        this.value = "";
    }

    /**
     * Gets the name of this variable.
     *
     * @return the variable name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this variable.
     *
     * @param name the variable name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the input keys this variable depends on.
     *
     * @return the list of input keys, or null if not set
     */
    public List<String> getInputKeys() {
        return inputKeys;
    }

    /**
     * Gets the current value of this variable.
     *
     * @return the variable value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of this variable.
     *
     * @param value the new value
     */
    protected void setValue(String value) {
        this.value = value;
    }

    /**
     * Updates the variable based on the provided keyword arguments.
     * Subclasses must implement this method to define their update logic.
     *
     * @param kwargs the key-value pairs for updating the variable
     */
    public abstract void update(Map<String, Object> kwargs);

    /**
     * Validates the input key-values, updates the value, and returns it.
     *
     * @param kwargs the input key-value pairs for updating the variable
     * @return the updated value of the variable
     */
    public String eval(Map<String, Object> kwargs) {
        Map<String, Object> inputKwargs = prepareInputs(kwargs);
        update(inputKwargs);
        return value;
    }

    /**
     * Prepares input key-value pairs by filtering only the keys this variable depends on.
     *
     * @param kwargs the full set of key-value pairs
     * @return a filtered map containing only relevant keys
     */
    public Map<String, Object> prepareInputs(Map<String, Object> kwargs) {
        if (inputKeys == null) {
            return new HashMap<>();
        }
        Map<String, Object> inputKwargs = new HashMap<>();
        for (String key : inputKeys) {
            if (kwargs.containsKey(key)) {
                inputKwargs.put(key, kwargs.get(key));
            }
        }
        return inputKwargs;
    }
}

