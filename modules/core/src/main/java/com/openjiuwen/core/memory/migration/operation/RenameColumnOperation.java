/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Rename a column in a table.
 * 
 * @since 0.1.7
 */
public class RenameColumnOperation extends BaseOperation {
    private final String table;
    private final String oldColumnName;
    private final String newColumnName;

    /**
     * RenameColumnOperation.
     * 
     * @param metadata metadata
     * @param table table
     * @param oldColumnName oldColumnName
     * @param newColumnName newColumnName
     * @since 0.1.7
     */
    public RenameColumnOperation(OperationMetadata metadata, String table, String oldColumnName, String newColumnName) {
        super(metadata);
        this.table = table;
        this.oldColumnName = oldColumnName;
        this.newColumnName = newColumnName;
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
     * getOldColumnName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOldColumnName() {
        return oldColumnName;
    }

    /**
     * getNewColumnName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNewColumnName() {
        return newColumnName;
    }
}
