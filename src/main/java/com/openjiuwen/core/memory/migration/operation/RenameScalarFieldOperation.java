/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Rename a scalar field in a vector data type.
 */
public class RenameScalarFieldOperation extends BaseOperation {
    private final String dataType;
    private final String oldFieldName;
    private final String newFieldName;

    public RenameScalarFieldOperation(OperationMetadata metadata, String dataType,
                                      String oldFieldName, String newFieldName) {
        super(metadata);
        this.dataType = dataType;
        this.oldFieldName = oldFieldName;
        this.newFieldName = newFieldName;
    }

    public String getDataType() {
        return dataType;
    }

    public String getOldFieldName() {
        return oldFieldName;
    }

    public String getNewFieldName() {
        return newFieldName;
    }
}