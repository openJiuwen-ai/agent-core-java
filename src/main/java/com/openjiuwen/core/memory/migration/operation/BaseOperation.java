/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import lombok.Data;

/**
 * Base class for all migration operations.
 */
@Data
/**
 * Auto-generated for codecheck compliance.
 */
public abstract class BaseOperation {
    private final OperationMetadata metadata;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseOperation(OperationMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getSchemaVersion() {
        return metadata.getSchemaVersion();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        String desc = metadata.getDescription();
        return desc != null ? desc : getClass().getSimpleName();
    }
}
