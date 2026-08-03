/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble.variables;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code Variable} in
 * {@code openjiuwen/core/foundation/prompt/assemble/variables/variable.py}.
 */
public abstract class Variable {

    protected String name;
    protected List<String> inputKeys;
    protected Object value = "";

    protected Variable(String name, List<String> inputKeys) {
        this.name = name;
        this.inputKeys = inputKeys;
    }

    public abstract Object update(Map<String, Object> kwargs);

    public Object eval(Map<String, Object> kwargs) {
        Map<String, Object> inputKwargs = prepareInputs(kwargs);
        update(inputKwargs);
        return value;
    }

    protected Map<String, Object> prepareInputs(Map<String, Object> kwargs) {
        Map<String, Object> inputKwargs = new LinkedHashMap<>();
        if (kwargs == null || inputKeys == null) {
            return inputKwargs;
        }
        for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
            if (inputKeys.contains(entry.getKey())) {
                inputKwargs.put(entry.getKey(), entry.getValue());
            }
        }
        return inputKwargs;
    }

    public Map<String, Object> publicPrepareInputs(Map<String, Object> kwargs) {
        return prepareInputs(kwargs);
    }

    public String getName() {
        return name;
    }

    public List<String> getInputKeys() {
        return inputKeys;
    }

    public Object getValue() {
        return value;
    }
}
