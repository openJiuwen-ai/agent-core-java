/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Update the data type of an existing column.
 * 
 * @since 0.1.7
 */
public class UpdateColumnTypeOperation extends BaseOperation {
    private final String table;
    private final String columnName;
    private final String newColumnType;

    /**
     * UpdateColumnTypeOperation.
     * 
     * @param metadata metadata
     * @param table table
     * @param columnName columnName
     * @param newColumnType newColumnType
     * @since 0.1.7
     */
    public UpdateColumnTypeOperation(OperationMetadata metadata, String table, String columnName,
            String newColumnType) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.newColumnType = newColumnType;
    }

    /**
     * getTable.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTable() {
        return table;
    }

    /**
     * getColumnName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * getNewColumnType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNewColumnType() {
        return newColumnType;
    }
}
