/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

/**
 * Supported vector store providers.
 * 
 * @since 0.1.7
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
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String value() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
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
