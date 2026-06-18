/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * <p>Mirrors Python's {@code UpdateScalarFieldTypeOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class UpdateScalarFieldTypeOperation extends BaseOperation {

    private final String dataType;
    private final String fieldName;
    private final String newFieldType;

    public UpdateScalarFieldTypeOperation(
            OperationMetadata metadata,
            String dataType,
            String fieldName,
            String newFieldType
    ) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newFieldType = newFieldType;
    }

    public String getDataType() {
        return dataType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getNewFieldType() {
        return newFieldType;
    }
}
