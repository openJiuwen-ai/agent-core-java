/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import java.util.function.Function;

/**
 * Update the embedding dimension of a vector data type.
 * 
 * @since 0.1.7
 */
public class UpdateEmbeddingDimensionOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final int newDimension;
    private final Function<Object, Object> recomputeEmbeddingFunc;
    private final int batchSize;

    /**
     * UpdateEmbeddingDimensionOperation.
     * 
     * @param metadata metadata
     * @param dataType dataType
     * @param fieldName fieldName
     * @param newDimension newDimension
     * @param batchSize batchSize
     * @since 0.1.7
     */
    public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType, String fieldName,
            int newDimension, int batchSize) {
        this(metadata, dataType, fieldName, newDimension, null, batchSize);
    }

    /**
     * UpdateEmbeddingDimensionOperation.
     * 
     * @param metadata metadata
     * @param dataType dataType
     * @param fieldName fieldName
     * @param newDimension newDimension
     * @param recomputeEmbeddingFunc recomputeEmbeddingFunc
     * @param batchSize batchSize
     * @since 0.1.7
     */
    public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType, String fieldName,
            int newDimension, Function<Object, Object> recomputeEmbeddingFunc, int batchSize) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newDimension = newDimension;
        this.recomputeEmbeddingFunc = recomputeEmbeddingFunc;
        this.batchSize = batchSize;
    }

    /**
     * getDataType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDataType() {
        return dataType;
    }

    /**
     * getFieldName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * getNewDimension.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getNewDimension() {
        return newDimension;
    }

    /**
     * getRecomputeEmbeddingFunc.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Function<Object, Object> getRecomputeEmbeddingFunc() {
        return recomputeEmbeddingFunc;
    }

    /**
     * getBatchSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getBatchSize() {
        return batchSize;
    }
}
