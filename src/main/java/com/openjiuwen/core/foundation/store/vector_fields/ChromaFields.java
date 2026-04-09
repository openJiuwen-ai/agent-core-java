/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.store.vector_fields;

import com.openjiuwen.spi.store.vector.CollectionSchema;

/**
 * Chroma-compatible field helpers.
 */
public final class ChromaFields {

    private ChromaFields() {
    }

    public static CollectionSchema defaultSchema(int dimension) {
        return BaseVectorFields.defaultSchema("embedding", dimension);
    }
}
