/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code DependencyType} in
 * {@code openjiuwen/core/controller/legacy/task/task.py}.
 */
public enum DependencyType {
    SEQUENTIAL("sequential"),
    PARALLEL("parallel"),
    CONDITIONAL("conditional"),
    DATA("data");

    private final String value;

    DependencyType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DependencyType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (DependencyType dependencyType : values()) {
            if (dependencyType.value.equals(value)) {
                return dependencyType;
            }
        }
        throw new IllegalArgumentException("Unknown dependency type: " + value);
    }
}
