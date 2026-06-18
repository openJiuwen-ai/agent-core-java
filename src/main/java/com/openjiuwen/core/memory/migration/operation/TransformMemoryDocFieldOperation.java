/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import java.util.Objects;
import java.util.function.Function;

/**
 * <p>Mirrors Python's {@code TransformMemoryDocFieldOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class TransformMemoryDocFieldOperation extends BaseOperation {

    private final String fieldName;
    private final Function<Object, Object> transformFunc;

    public TransformMemoryDocFieldOperation(
            OperationMetadata metadata,
            String fieldName,
            Function<Object, Object> transformFunc
    ) {
        super(metadata);
        this.fieldName = fieldName;
        this.transformFunc = Objects.requireNonNull(transformFunc, "transformFunc");
    }

    public String getFieldName() {
        return fieldName;
    }

    public Function<Object, Object> getTransformFunc() {
        return transformFunc;
    }
}
