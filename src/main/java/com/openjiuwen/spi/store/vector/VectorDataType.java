/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

/**
 * Supported data types for vector store fields.
 * <p>
 * Mirrors Python's {@code VectorDataType} enum.
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
    ARRAY
}
