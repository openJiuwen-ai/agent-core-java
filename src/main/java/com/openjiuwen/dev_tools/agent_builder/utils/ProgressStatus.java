/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

/**
 * Public enum ProgressStatus used by the Java parity implementation.
 *
 * @since 1.0
 */
public enum ProgressStatus {
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    ProgressStatus(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }
}
