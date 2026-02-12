// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.constants;

/**
 * Controller type enumeration
 * 
 * <p>Defines the types of controllers available in the agent system.
 * 
 * @since 0.1.4
 */
public enum ControllerType {
    
    /** ReAct controller type */
    REACT_CONTROLLER("react"),
    
    /** Workflow controller type */
    WORKFLOW_CONTROLLER("workflow"),
    
    /** Undefined controller type */
    UNDEFINED("undefined");
    
    private final String value;
    
    ControllerType(String value) {
        this.value = value;
    }
    
    /**
     * Gets the string value of this controller type
     * 
     * @return the string value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Gets ControllerType from string value
     * 
     * @param value the string value
     * @return the corresponding ControllerType, or UNDEFINED if not found
     */
    public static ControllerType fromValue(String value) {
        if (value == null) {
            return UNDEFINED;
        }
        for (ControllerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNDEFINED;
    }
    
    @Override
    public String toString() {
        return value;
    }
}

