/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

/**
 * Record representing extracted data from LLM processing.
 * Corresponds to Python: process/extract/memory_info.py ExtractedData
 */
public record ExtractedData(
        ExtractedDataType type,
        String key,
        String value
) {
}

