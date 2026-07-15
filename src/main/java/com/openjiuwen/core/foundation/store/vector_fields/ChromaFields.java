/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import com.openjiuwen.spi.store.vector.CollectionSchema;

/**
 * Chroma-compatible field helpers.
 * 
 * @since 0.1.7
 */
public final class ChromaFields {
    /**
     * ChromaFields.
     * 
     * @since 0.1.7
     */
    private ChromaFields() {
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
