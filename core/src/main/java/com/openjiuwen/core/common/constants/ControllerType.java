/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code ControllerType} in
 * {@code openjiuwen/core/common/constants/enums.py}.
 */
public enum ControllerType {
    REACT_CONTROLLER("react"),
    WORKFLOW_CONTROLLER("workflow"),
    UNDEFINED("undefined");

    private final String value;

    ControllerType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
