/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code StoreType} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
public enum StoreType {
    MILVUS("milvus"),
    CHROMA("chroma"),
    PGVECTOR("pgvector");

    private final String value;

    StoreType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StoreType fromValue(String value) {
        for (StoreType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported store provider: " + value);
    }
}
