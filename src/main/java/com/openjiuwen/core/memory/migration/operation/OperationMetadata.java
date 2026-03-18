/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.migration.operation;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Simple operation metadata.
 */
@Data
@AllArgsConstructor
public class OperationMetadata {
    private int schemaVersion;
    private String description;

    public OperationMetadata(int schemaVersion) {
        this.schemaVersion = schemaVersion;
        this.description = null;
    }
}
