/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Rename a scalar field in a vector data type.
 * 
 * @since 0.1.7
 */
public class RenameScalarFieldOperation extends BaseOperation {
    private final String dataType;
    private final String oldFieldName;
    private final String newFieldName;

    /**
     * RenameScalarFieldOperation.
     * 
     * @param metadata metadata
     * @param dataType dataType
     * @param oldFieldName oldFieldName
     * @param newFieldName newFieldName
     * @since 0.1.7
     */
    public RenameScalarFieldOperation(OperationMetadata metadata, String dataType, String oldFieldName,
            String newFieldName) {
        super(metadata);
        this.dataType = dataType;
        this.oldFieldName = oldFieldName;
        this.newFieldName = newFieldName;
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
     * getOldFieldName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOldFieldName() {
        return oldFieldName;
    }

    /**
     * getNewFieldName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNewFieldName() {
        return newFieldName;
    }
}
