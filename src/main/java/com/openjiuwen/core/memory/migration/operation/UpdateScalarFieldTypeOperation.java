/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Update the data type of a scalar field in a vector data type.
 * 
 * @since 0.1.7
 */
public class UpdateScalarFieldTypeOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final String newFieldType;

    /**
     * UpdateScalarFieldTypeOperation.
     * 
     * @param metadata metadata
     * @param dataType dataType
     * @param fieldName fieldName
     * @param newFieldType newFieldType
     * @since 0.1.7
     */
    public UpdateScalarFieldTypeOperation(OperationMetadata metadata, String dataType, String fieldName,
            String newFieldType) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newFieldType = newFieldType;
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
     * getNewFieldType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNewFieldType() {
        return newFieldType;
    }
}
