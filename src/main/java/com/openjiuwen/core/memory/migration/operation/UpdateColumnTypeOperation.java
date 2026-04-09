/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.migration.operation;

/**
 * Update the data type of an existing column.
 */
public class UpdateColumnTypeOperation extends BaseOperation {
    private final String table;
    private final String columnName;
    private final String newColumnType;

    public UpdateColumnTypeOperation(OperationMetadata metadata, String table,
                                     String columnName, String newColumnType) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.newColumnType = newColumnType;
    }

    public String getTable() {
        return table;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getNewColumnType() {
        return newColumnType;
    }
}