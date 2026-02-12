/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.JiuWenBaseException;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents interactive input for workflow/agent resumption.
 * 
 * <p>Contains user inputs bound to specific node IDs and raw inputs
 * for the first interaction.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InteractiveInput {
    
    private static final Object SENTINEL = new Object();
    
    private final Map<String, Object> userInputs;
    private Object rawInputs;
    
    /**
     * Creates a new InteractiveInput with raw inputs.
     * 
     * @param rawInputs the raw inputs, or null to use sentinel
     * @throws JiuWenBaseException if rawInputs is explicitly null
     */
    public InteractiveInput(Object rawInputs) {
        this.userInputs = new HashMap<>();
        
        if (rawInputs == null) {
            throw new JiuWenBaseException(-1, "value of raw_inputs is none");
        }
        
        if (rawInputs == SENTINEL) {
            this.rawInputs = null;
        } else {
            this.rawInputs = rawInputs;
        }
    }
    
    /**
     * Creates a new empty InteractiveInput.
     */
    public InteractiveInput() {
        this.userInputs = new HashMap<>();
        this.rawInputs = null;
    }
    
    /**
     * Creates an InteractiveInput with the sentinel marker (empty state).
     * 
     * @return an empty InteractiveInput
     */
    public static InteractiveInput empty() {
        InteractiveInput input = new InteractiveInput();
        return input;
    }
    
    /**
     * Updates user inputs for a specific node.
     * 
     * @param nodeId the node identifier
     * @param value the input value
     * @throws JiuWenBaseException if rawInputs exists or nodeId/value is null
     */
    public void update(String nodeId, Object value) {
        if (rawInputs != null) {
            throw new JiuWenBaseException(-1, 
                "raw_inputs existed, update is invalid");
        }
        if (nodeId == null || value == null) {
            throw new JiuWenBaseException(-1, 
                "value is none or node_id is none");
        }
        userInputs.put(nodeId, value);
    }
    
    /**
     * Gets user inputs map.
     * 
     * @return the user inputs
     */
    public Map<String, Object> getUserInputs() {
        return userInputs;
    }
    
    /**
     * Gets raw inputs.
     * 
     * @return the raw inputs
     */
    public Object getRawInputs() {
        return rawInputs;
    }
    
    /**
     * Checks if this input has raw inputs.
     * 
     * @return true if raw inputs exist
     */
    public boolean hasRawInputs() {
        return rawInputs != null;
    }
}

