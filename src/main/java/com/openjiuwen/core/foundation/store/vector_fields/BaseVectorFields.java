/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;

import java.util.List;

/**
 * Reusable helpers for building vector collection schemas.
 */
public final class BaseVectorFields {

    private BaseVectorFields() {
    }

    public static CollectionSchema defaultSchema(String vectorFieldName, int dimension) {
        return CollectionSchema.fromFields(List.of(
                FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(256).build(),
                FieldSchema.builder().name(vectorFieldName).dtype(VectorDataType.FLOAT_VECTOR).dim(dimension).build(),
                FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()
        ), "Default vector schema", false);
    }
}
