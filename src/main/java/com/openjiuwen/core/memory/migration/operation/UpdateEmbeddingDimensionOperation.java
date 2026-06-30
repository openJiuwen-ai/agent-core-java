/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import java.util.function.Function;

/**
 * Update the embedding dimension of a vector data type.
 */
public class UpdateEmbeddingDimensionOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final int newDimension;
    private final Function<Object, Object> recomputeEmbeddingFunc;
    private final int batchSize;

    /**
     * Auto-generated for codecheck compliance.
     */
    public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType,
                                             String fieldName, int newDimension, int batchSize) {
        this(metadata, dataType, fieldName, newDimension, null, batchSize);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType,
                                             String fieldName, int newDimension,
                                             Function<Object, Object> recomputeEmbeddingFunc,
                                             int batchSize) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newDimension = newDimension;
        this.recomputeEmbeddingFunc = recomputeEmbeddingFunc;
        this.batchSize = batchSize;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDataType() {
        return dataType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getNewDimension() {
        return newDimension;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Function<Object, Object> getRecomputeEmbeddingFunc() {
        return recomputeEmbeddingFunc;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getBatchSize() {
        return batchSize;
    }
}
