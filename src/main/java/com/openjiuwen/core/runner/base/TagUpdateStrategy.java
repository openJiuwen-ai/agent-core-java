/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Compatibility facade for the 0.1.12 runner-base tag update enum.
 *
 * <p>Mirrors Python's {@code TagUpdateStrategy} in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 */
public enum TagUpdateStrategy {
    MERGE("merge"),
    REPLACE("isReplace");

    private final String value;

    TagUpdateStrategy(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String getValue() {
        return value;
    }

    public com.openjiuwen.core.runner.resourcemanager.TagUpdateStrategy toResourceManagerStrategy() {
        return this == REPLACE
                ? com.openjiuwen.core.runner.resourcemanager.TagUpdateStrategy.REPLACE
                : com.openjiuwen.core.runner.resourcemanager.TagUpdateStrategy.MERGE;
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
