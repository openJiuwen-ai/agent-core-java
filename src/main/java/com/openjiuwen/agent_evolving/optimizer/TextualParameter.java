  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.agent_evolving.optimizer;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Gradient container for operator_id.
 *
 * <p>Stores target -> gradient text and optional description.
 * No longer holds Operator reference.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.base.TextualParameter}.
 */
@Data
public class TextualParameter {

    private final String operatorId;
    private Map<String, String> gradients = new HashMap<>();
    private String description = "";

    /**
     * Create with operator ID.
     *
     * @param operatorId Operator identifier
     */
    public TextualParameter(String operatorId) {
        this.operatorId = operatorId;
    }

    /**
     * Set gradient for a target.
     *
     * @param name     Target name
     * @param gradient Gradient text
     */
    public void setGradient(String name, String gradient) {
        gradients.put(name, gradient);
    }

    /**
     * Get gradient for a target.
     *
     * @param name Target name
     * @return Gradient text or null
     */
    public String getGradient(String name) {
        return gradients.get(name);
    }
}