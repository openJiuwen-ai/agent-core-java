/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Simple metadata for migration operations.
 *
 * <p>Mirrors Python's {@code OperationMetadata} in
 * {@code openjiuwen/core/memory/migration/operation/base_operation.py}.</p>
 */
@Data
@AllArgsConstructor
public class OperationMetadata {

    private int schemaVersion;
    private String description;

    public OperationMetadata(int schemaVersion) {
        this(schemaVersion, null);
    }
}
