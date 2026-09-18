/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

/**
 * Mirrors Python's {@code VectorDataType} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.
 */
public enum VectorDataType {
    VARCHAR,
    FLOAT_VECTOR,
    INT64,
    INT32,
    INT16,
    INT8,
    FLOAT,
    DOUBLE,
    BOOL,
    JSON,
    ARRAY;

    public static VectorDataType fromValue(String value) {
        return value == null ? VARCHAR : VectorDataType.valueOf(value.toUpperCase());
    }
}
