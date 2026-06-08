/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import lombok.Getter;

/**
 * Base class for all operations. Pure DTO, no execution logic.
 *
 * <p>Mirrors Python's {@code BaseOperation} in
 * {@code openjiuwen/core/memory/migration/operation/base_operation.py}.</p>
 */
@Getter
public abstract class BaseOperation {

    private final OperationMetadata metadata;

    protected BaseOperation(OperationMetadata metadata) {
        this.metadata = metadata;
    }

    public int getSchemaVersion() {
        return metadata.getSchemaVersion();
    }

    public String getDescription() {
        String description = metadata.getDescription();
        return description != null ? description : getClass().getSimpleName();
    }
}
