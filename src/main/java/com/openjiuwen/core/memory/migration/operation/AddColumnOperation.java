/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public AddColumnOperation(OperationMetadata metadata, String table, String columnName,
                              String columnType, boolean nullable, Object defaultValue) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.columnType = columnType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTable() {
        return table;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getColumnType() {
        return columnType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getDefaultValue() {
        return defaultValue;
    }
}
