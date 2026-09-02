/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Simple operation metadata.
 * 
 * @since 0.1.7
 */
@Data
@AllArgsConstructor
public class OperationMetadata {
    private int schemaVersion;
    private String description;

    /**
     * OperationMetadata.
     * 
     * @param schemaVersion schemaVersion
     * @since 0.1.7
     */
    public OperationMetadata(int schemaVersion) {
        this.schemaVersion = schemaVersion;
        this.description = null;
    }
}
