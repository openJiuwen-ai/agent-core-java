/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Compatibility facade for the 0.1.12 runner-base tag matching enum.
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

    public String getValue() {
        return value;
    }

    public com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy toResourceManagerStrategy() {
        return com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy.fromValue(value);
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
