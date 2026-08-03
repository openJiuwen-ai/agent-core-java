/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

/**
 * <p>Mirrors Python's {@code RemoveMemoryDocFieldOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class RemoveMemoryDocFieldOperation extends BaseOperation {

    private final String fieldName;

    public RemoveMemoryDocFieldOperation(OperationMetadata metadata, String fieldName) {
        super(metadata);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
