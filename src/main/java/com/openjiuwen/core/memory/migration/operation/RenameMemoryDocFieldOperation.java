/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * <p>Mirrors Python's {@code RenameMemoryDocFieldOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class RenameMemoryDocFieldOperation extends BaseOperation {

    private final String oldFieldName;
    private final String newFieldName;

    public RenameMemoryDocFieldOperation(
            OperationMetadata metadata,
            String oldFieldName,
            String newFieldName
    ) {
        super(metadata);
        this.oldFieldName = oldFieldName;
        this.newFieldName = newFieldName;
    }

    public String getOldFieldName() {
        return oldFieldName;
    }

    public String getNewFieldName() {
        return newFieldName;
    }
}
