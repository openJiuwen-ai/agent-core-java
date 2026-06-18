/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import java.util.function.Function;

/**
 * <p>Mirrors Python's {@code UpdateEmbeddingDimensionOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class UpdateEmbeddingDimensionOperation extends BaseOperation {

    private final String dataType;
    private final String fieldName;
    private final int newDimension;
    private final Function<Object, Object> recomputeEmbeddingFunc;
    private final int batchSize;

    public UpdateEmbeddingDimensionOperation(
            OperationMetadata metadata,
            String dataType,
            String fieldName,
            int newDimension
    ) {
        this(metadata, dataType, fieldName, newDimension, null, 1000);
    }

    public UpdateEmbeddingDimensionOperation(
            OperationMetadata metadata,
            String dataType,
            String fieldName,
            int newDimension,
            Function<Object, Object> recomputeEmbeddingFunc,
            int batchSize
    ) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newDimension = newDimension;
        this.recomputeEmbeddingFunc = recomputeEmbeddingFunc;
        this.batchSize = batchSize;
    }

    public String getDataType() {
        return dataType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getNewDimension() {
        return newDimension;
    }

    public Function<Object, Object> getRecomputeEmbeddingFunc() {
        return recomputeEmbeddingFunc;
    }

    public int getBatchSize() {
        return batchSize;
    }
}
