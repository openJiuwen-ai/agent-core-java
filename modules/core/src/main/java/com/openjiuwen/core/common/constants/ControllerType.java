/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Controller type enumeration.
 * <p>
 * Defines the supported controller types for agent orchestration.
 * </p>
 * 
 * @since 0.1.7
 */
public enum ControllerType {
    REACT_CONTROLLER("react"),
    WORKFLOW_CONTROLLER("workflow"),
    UNDEFINED("undefined");

    private final String value;

    ControllerType(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    @JsonCreator
    public static ControllerType fromValue(String value) {
        for (ControllerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNDEFINED;
    }
}
