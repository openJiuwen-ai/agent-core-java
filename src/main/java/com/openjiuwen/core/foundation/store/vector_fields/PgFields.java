/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import com.openjiuwen.spi.store.vector.CollectionSchema;

/**
 * PGVector-compatible field helpers.
 */
public final class PgFields {

    private PgFields() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static CollectionSchema defaultSchema(int dimension) {
        return BaseVectorFields.defaultSchema("embedding", dimension);
    }
}
