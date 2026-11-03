/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gradient container for operator id.
 *
 * <p>Mirrors Python's {@code TextualParameter} in
 * {@code openjiuwen/agent_evolving/optimizer/base.py}.</p>
 */
public class TextualParameter {

    private final String operatorId;
    private final Map<String, Object> gradients = new LinkedHashMap<>();
    private String description = "";

    public TextualParameter(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setGradient(String name, Object gradient) {
        gradients.put(name, gradient);
    }

    public Object getGradient(String name) {
        return gradients.get(name);
    }

    public Map<String, Object> getGradients() {
        return new LinkedHashMap<>(gradients);
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getDescription() {
        return description;
    }
}
