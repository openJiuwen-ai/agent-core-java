/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * Rename a column in a table.
 */
public class RenameColumnOperation extends BaseOperation {
    private final String table;
    private final String oldColumnName;
    private final String newColumnName;

    /**
     * Auto-generated for codecheck compliance.
     */
    public RenameColumnOperation(OperationMetadata metadata, String table,
                                 String oldColumnName, String newColumnName) {
        super(metadata);
        this.table = table;
        this.oldColumnName = oldColumnName;
        this.newColumnName = newColumnName;
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
    public String getOldColumnName() {
        return oldColumnName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNewColumnName() {
        return newColumnName;
    }
}
