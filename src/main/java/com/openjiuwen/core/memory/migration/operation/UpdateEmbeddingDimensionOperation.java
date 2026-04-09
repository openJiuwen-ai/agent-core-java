  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Update the embedding dimension of a vector data type.
 */
public class UpdateEmbeddingDimensionOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final int newDimension;
    private final int batchSize;

    public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType,
                                             String fieldName, int newDimension, int batchSize) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newDimension = newDimension;
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

    public int getBatchSize() {
        return batchSize;
    }
}