/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.constants;

/**
 * Controller type enumeration.
 *
 * <p>Defines the supported controller types for agent orchestration.</p>
 */
public enum ControllerType {

    REACT_CONTROLLER("react"),
    WORKFLOW_CONTROLLER("workflow"),
    UNDEFINED("undefined");

    private final String value;

    ControllerType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a string value into the corresponding {@link ControllerType}.
     *
     * @param value the string representation
     * @return the matching enum constant, or {@link #UNDEFINED} if no match
     */
    public static ControllerType fromValue(String value) {
        for (ControllerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNDEFINED;
    }
}
