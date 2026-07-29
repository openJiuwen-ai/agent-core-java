/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Interactive input data carrying user inputs for interactions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interactive_input.InteractiveInput}.
 * Values stored in this object must also be serializable when a persistent checkpointer uses Java serialization.
 * 
 * @since 0.1.7
 */
public class InteractiveInput implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Map<String, Object> userInputs;

    /**
     * Raw input not bound to any ID, used for the first interaction.
     */
    private Object rawInputs;

    /**
     * Create with no inputs.
     * 
     * @since 0.1.7
     */
    public InteractiveInput() {
        this.userInputs = new HashMap<>();
        this.rawInputs = null;
    }

    /**
     * Create with raw inputs.
     * 
     * @param rawInputs the raw input value; must not be null
     * @since 0.1.7
     */
    public InteractiveInput(Object rawInputs) {
        if (rawInputs == null) {
            throw ErrorHelper.buildError(StatusCode.INTERACTION_INPUT_INVALID, "reason", "value of raw_inputs is none");
        }
        this.userInputs = new HashMap<>();
        this.rawInputs = rawInputs;
    }

    /**
     * getUserInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getUserInputs() {
        return userInputs;
    }

    /**
     * setUserInputs.
     * 
     * @param userInputs userInputs
     * @since 0.1.7
     */
    public void setUserInputs(Map<String, Object> userInputs) {
        this.userInputs = userInputs;
    }

    /**
     * getRawInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getRawInputs() {
        return rawInputs;
    }

    /**
     * setRawInputs.
     * 
     * @param rawInputs rawInputs
     * @since 0.1.7
     */
    public void setRawInputs(Object rawInputs) {
        this.rawInputs = rawInputs;
    }

    /**
     * Update user inputs for a specific node.
     * 
     * @param nodeId the node ID
     * @param value the input value
     * @since 0.1.7
     */
    public void update(String nodeId, Object value) {
        if (rawInputs != null) {
            throw ErrorHelper.buildError(StatusCode.INTERACTION_INPUT_INVALID, "reason",
                    "raw_inputs isExisted, update is invalid");
        }
        if (nodeId == null || value == null) {
            throw ErrorHelper.buildError(StatusCode.INTERACTION_INPUT_INVALID, "reason",
                    "value is none or node_id is none");
        }
        userInputs.put(nodeId, value);
    }
}
