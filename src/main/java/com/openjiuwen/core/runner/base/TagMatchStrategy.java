/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Strategy for matching multiple tags when querying or filtering resources.
 * <p>
 * Mirrors Python's {@code TagMatchStrategy}.
 * 
 * @since 0.1.7
 */
public enum TagMatchStrategy {
    ALL("all"),
    /** Resource must contain ANY of the specified tags. */
    ANY("any");

    private final String value;

    TagMatchStrategy(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
