/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Supported memory types for vector operations.
 * 
 * @since 0.1.7
 */
public enum SupportMemoryType {
    USER_PROFILE("user_profile"),
    SUMMARY("summary");

    private final String value;

    SupportMemoryType(String value) {
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
