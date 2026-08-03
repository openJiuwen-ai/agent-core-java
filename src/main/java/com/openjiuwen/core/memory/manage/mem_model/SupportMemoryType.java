/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Memory types supported by summary/vector-specific paths.
 *
 * <p>Mirrors Python's {@code SupportMemoryType} in
 * {@code openjiuwen/core/memory/manage/mem_model/memory_unit.py}.</p>
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
