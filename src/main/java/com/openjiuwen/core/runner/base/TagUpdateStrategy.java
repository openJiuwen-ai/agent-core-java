/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Strategy for updating resource tags.
 * <p>
 * Mirrors Python's {@code TagUpdateStrategy}.
 */
public enum TagUpdateStrategy {
    /** Merge new tags with existing tags. */
    MERGE("merge"),
    /** Replace all existing tags with new tags. */
    REPLACE("isReplace");

    private final String value;

    TagUpdateStrategy(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }
}
