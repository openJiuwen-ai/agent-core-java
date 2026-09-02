/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Add a new column to a table.
 * 
 * @since 0.1.7
 */
public class AddColumnOperation extends BaseOperation {
    private final String table;
    private final String columnName;
    private final String columnType;
    private final boolean nullable;
    private final Object defaultValue;

    /**
     * AddColumnOperation.
     * 
     * @param metadata metadata
     * @param table table
     * @param columnName columnName
     * @param columnType columnType
     * @param nullable nullable
     * @param defaultValue defaultValue
     * @since 0.1.7
     */
    public AddColumnOperation(OperationMetadata metadata, String table, String columnName, String columnType,
            boolean nullable, Object defaultValue) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.columnType = columnType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
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
     * getColumnType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getColumnType() {
        return columnType;
    }

    /**
     * isNullable.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * getDefaultValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getDefaultValue() {
        return defaultValue;
    }
}
