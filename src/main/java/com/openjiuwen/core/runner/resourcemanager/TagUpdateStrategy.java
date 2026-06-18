/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Strategies for updating resource tags.
 *
 * <p>Mirrors Python's {@code TagUpdateStrategy} in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 */
public enum TagUpdateStrategy {
    MERGE("merge"),
    REPLACE("replace");

    private final String value;

    TagUpdateStrategy(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TagUpdateStrategy fromValue(String value) {
        for (TagUpdateStrategy strategy : values()) {
            if (strategy.value.equals(value)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unsupported tag update strategy: " + value);
    }
}
