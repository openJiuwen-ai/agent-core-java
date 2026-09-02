/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import lombok.Data;

/**
 * BaseOperation.
 * 
 * @since 0.1.7
 */
@Data
public abstract class BaseOperation {
    private final OperationMetadata metadata;

    /**
     * BaseOperation.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    protected BaseOperation(OperationMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * getSchemaVersion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getSchemaVersion() {
        return metadata.getSchemaVersion();
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        String desc = metadata.getDescription();
        return desc != null ? desc : getClass().getSimpleName();
    }
}
