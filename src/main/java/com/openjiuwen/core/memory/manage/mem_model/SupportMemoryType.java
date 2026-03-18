/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Supported memory types for vector operations.
 */
public enum SupportMemoryType {
    USER_PROFILE("user_profile"),
    SUMMARY("summary");

    private final String value;

    SupportMemoryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
