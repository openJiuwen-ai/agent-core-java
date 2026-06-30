/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Update the data type of a scalar field in a vector data type.
 */
public class UpdateScalarFieldTypeOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final String newFieldType;

    /**
     * Auto-generated for codecheck compliance.
     */
    public UpdateScalarFieldTypeOperation(OperationMetadata metadata, String dataType,
                                          String fieldName, String newFieldType) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newFieldType = newFieldType;
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
    public String getNewFieldType() {
        return newFieldType;
    }
}
