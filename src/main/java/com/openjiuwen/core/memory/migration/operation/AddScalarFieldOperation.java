/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Add a scalar field to a vector data type.
 * 
 * @since 0.1.7
 */
public class AddScalarFieldOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final String fieldType;
    private final Object defaultValue;

    /**
     * AddScalarFieldOperation.
     * 
     * @param metadata metadata
     * @param dataType dataType
     * @param fieldName fieldName
     * @param fieldType fieldType
     * @param defaultValue defaultValue
     * @since 0.1.7
     */
    public AddScalarFieldOperation(OperationMetadata metadata, String dataType, String fieldName, String fieldType,
            Object defaultValue) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.defaultValue = defaultValue;
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
     * getFieldType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getFieldType() {
        return fieldType;
    }

    /**
     * getDefaultValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getDefaultValue() {
        return defaultValue;
    }
}
