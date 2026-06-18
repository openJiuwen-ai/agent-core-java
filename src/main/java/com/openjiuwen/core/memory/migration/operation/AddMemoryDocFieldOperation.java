/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import java.util.function.Supplier;

/**
 * <p>Mirrors Python's {@code AddMemoryDocFieldOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class AddMemoryDocFieldOperation extends BaseOperation {

    private final String fieldName;
    private final Object defaultValueOrFunc;

    public AddMemoryDocFieldOperation(
            OperationMetadata metadata,
            String fieldName,
            Object defaultValueOrFunc
    ) {
        super(metadata);
        this.fieldName = fieldName;
        this.defaultValueOrFunc = defaultValueOrFunc;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getDefaultValueOrFunc() {
        return defaultValueOrFunc;
    }

    public Object resolveDefaultValue() {
        if (defaultValueOrFunc instanceof Supplier<?> supplier) {
            return supplier.get();
        }
        return defaultValueOrFunc;
    }
}
