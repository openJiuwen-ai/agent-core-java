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

    /**
     * Auto-generated for codecheck compliance.
     */
    public RenameScalarFieldOperation(OperationMetadata metadata, String dataType,
                                      String oldFieldName, String newFieldName) {
        super(metadata);
        this.dataType = dataType;
        this.oldFieldName = oldFieldName;
        this.newFieldName = newFieldName;
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
    public String getOldFieldName() {
        return oldFieldName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNewFieldName() {
        return newFieldName;
    }
}
