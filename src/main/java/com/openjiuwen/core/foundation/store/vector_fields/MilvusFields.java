/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import com.openjiuwen.spi.store.vector.CollectionSchema;

/**
 * Milvus-compatible field helpers.
 * 
 * @since 0.1.7
 */
public final class MilvusFields {
    /**
     * MilvusFields.
     * 
     * @since 0.1.7
     */
    private MilvusFields() {
    }

    /**
     * defaultSchema.
     * 
     * @param dimension dimension
     * @return the result
     * @since 0.1.7
     */
    public static CollectionSchema defaultSchema(int dimension) {
        return BaseVectorFields.defaultSchema("embedding", dimension);
    }
}
