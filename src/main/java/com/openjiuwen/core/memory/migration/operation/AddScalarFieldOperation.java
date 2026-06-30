/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Add a scalar field to a vector data type.
 */
public class AddScalarFieldOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final String fieldType;
    private final Object defaultValue;

    /**
     * Auto-generated for codecheck compliance.
     */
    public AddScalarFieldOperation(OperationMetadata metadata, String dataType,
                                   String fieldName, String fieldType, Object defaultValue) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.defaultValue = defaultValue;
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
    public String getFieldType() {
        return fieldType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getDefaultValue() {
        return defaultValue;
    }
}
