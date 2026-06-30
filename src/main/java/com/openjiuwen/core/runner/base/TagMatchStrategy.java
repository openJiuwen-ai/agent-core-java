/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Strategy for matching multiple tags when querying or filtering resources.
 * <p>
 * Mirrors Python's {@code TagMatchStrategy}.
 */
public enum TagMatchStrategy {
    /** Resource must contain ALL specified tags. */
    ALL("all"),
    /** Resource must contain ANY of the specified tags. */
    ANY("any");

    private final String value;

    TagMatchStrategy(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }
}
