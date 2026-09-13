/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.spi.store.BaseKVStore;

import java.util.function.Consumer;

/**
 * Update a key-value pair via a provided callable.
 * 
 * @since 0.1.7
 */
public class UpdateKVOperation extends BaseOperation {
    private final Consumer<BaseKVStore> updateFunc;

    /**
     * UpdateKVOperation.
     * 
     * @param metadata metadata
     * @param updateFunc updateFunc
     * @since 0.1.7
     */
    public UpdateKVOperation(OperationMetadata metadata, Consumer<BaseKVStore> updateFunc) {
        super(metadata);
        this.updateFunc = updateFunc;
    }

    /**
     * getUpdateFunc.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Consumer<BaseKVStore> getUpdateFunc() {
        return updateFunc;
    }
}
