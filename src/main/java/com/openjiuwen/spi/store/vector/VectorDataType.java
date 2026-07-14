/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

/**
 * Supported data types for vector store fields.
 *
 * <p>Mirrors Python's {@code VectorDataType} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.</p>
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

    static VectorDataType fromCore(com.openjiuwen.core.foundation.store.VectorDataType value) {
        return value == null ? null : VectorDataType.valueOf(value.name());
    }

    com.openjiuwen.core.foundation.store.VectorDataType toCore() {
        return com.openjiuwen.core.foundation.store.VectorDataType.valueOf(name());
    }
}
