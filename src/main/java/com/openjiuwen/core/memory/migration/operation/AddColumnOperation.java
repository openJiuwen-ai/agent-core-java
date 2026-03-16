/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.migration.operation;

/**
 * Add a new column to a table.
 */
public class AddColumnOperation extends BaseOperation {
    private final String table;
    private final String columnName;
    private final String columnType;
    private final boolean nullable;
    private final Object defaultValue;

    public AddColumnOperation(OperationMetadata metadata, String table, String columnName,
                              String columnType, boolean nullable, Object defaultValue) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.columnType = columnType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
    }

    public String getTable() {
        return table;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getColumnType() {
        return columnType;
    }

    public boolean isNullable() {
        return nullable;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}