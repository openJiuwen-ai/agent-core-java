/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

/**
 * Public enum ProgressStage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public enum ProgressStage {
    INITIALIZING("initializing"),
    CLARIFYING("clarifying"),
    GENERATING_CONFIG("generating_config"),
    COMPLETED("completed"),
    ERROR("error");

    private final String value;

    ProgressStage(String value) {
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
