/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * <p>Mirrors Python's {@code AddScalarFieldOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class AddScalarFieldOperation extends BaseOperation {

    private final String dataType;
    private final String fieldName;
    private final String fieldType;
    private final Object defaultValue;

    public AddScalarFieldOperation(
            OperationMetadata metadata,
            String dataType,
            String fieldName,
            String fieldType
    ) {
        this(metadata, dataType, fieldName, fieldType, null);
    }

    public AddScalarFieldOperation(
            OperationMetadata metadata,
            String dataType,
            String fieldName,
            String fieldType,
            Object defaultValue
    ) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.defaultValue = defaultValue;
    }

    public String getDataType() {
        return dataType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}
