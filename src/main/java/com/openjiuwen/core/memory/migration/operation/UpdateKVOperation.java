/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.core.foundation.store.BaseKVStore;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * <p>Mirrors Python's {@code UpdateKVOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class UpdateKVOperation extends BaseOperation {

    private final Function<BaseKVStore, CompletableFuture<Void>> updateFunc;

    public UpdateKVOperation(
            OperationMetadata metadata,
            Function<BaseKVStore, CompletableFuture<Void>> updateFunc
    ) {
        super(metadata);
        this.updateFunc = updateFunc;
    }

    public Function<BaseKVStore, CompletableFuture<Void>> getUpdateFunc() {
        return updateFunc;
    }
}
