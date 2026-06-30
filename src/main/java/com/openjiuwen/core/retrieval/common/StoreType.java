/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

/**
 * Supported vector store providers.
 */
public enum StoreType {
    MILVUS("milvus"),
    CHROMA("chroma"),
    PGVECTOR("pgvector"),
    ELASTICSEARCH("elasticsearch");

    private final String value;

    StoreType(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static StoreType fromValue(String value) {
        String normalized = RetrievalValidation.validateStoreType(value, "StoreType");
        for (StoreType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw RetrievalExceptions.validation("unsupported store type: " + value);
    }
}
