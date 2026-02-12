/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

/**
 * Enum representing types of extracted data.
 * Corresponds to Python: process/extract/memory_info.py ExtractedDataType
 */
public enum ExtractedDataType {
    USER("user");

    private final String value;

    ExtractedDataType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

