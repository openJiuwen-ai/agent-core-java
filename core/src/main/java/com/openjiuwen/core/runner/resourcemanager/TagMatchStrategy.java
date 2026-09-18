/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Strategies for matching resource tags.
 *
 * <p>Mirrors Python's {@code TagMatchStrategy} in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 */
public enum TagMatchStrategy {
    ALL("all"),
    ANY("any");

    private final String value;

    TagMatchStrategy(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TagMatchStrategy fromValue(String value) {
        for (TagMatchStrategy strategy : values()) {
            if (strategy.value.equals(value)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unsupported tag match strategy: " + value);
    }
}
