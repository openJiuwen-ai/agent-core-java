/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import lombok.Data;

/**
 * Base class for all migration operations.
 *
 * <p>Mirrors Python's {@code BaseOperation} in
 * {@code openjiuwen.core.memory.migration.operation.base_operation}.
 */
@Data
public abstract class BaseOperation {
    private final OperationMetadata metadata;

    protected BaseOperation(OperationMetadata metadata) {
        this.metadata = metadata;
    }

    public int getSchemaVersion() {
        return metadata.getSchemaVersion();
    }

    public String getDescription() {
        String desc = metadata.getDescription();
        return desc != null ? desc : getClass().getSimpleName();
    }
}
