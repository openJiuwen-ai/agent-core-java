/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.spi.store.BaseKVStore;

import java.util.function.Consumer;

/**
 * Update a key-value pair via a provided callable.
 */
public class UpdateKVOperation extends BaseOperation {
    private final Consumer<BaseKVStore> updateFunc;

    /**
     * Auto-generated for codecheck compliance.
     */
    public UpdateKVOperation(OperationMetadata metadata, Consumer<BaseKVStore> updateFunc) {
        super(metadata);
        this.updateFunc = updateFunc;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Consumer<BaseKVStore> getUpdateFunc() {
        return updateFunc;
    }
}
