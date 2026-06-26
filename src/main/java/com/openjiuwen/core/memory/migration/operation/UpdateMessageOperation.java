/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.core.foundation.store.BaseMessageStore;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * <p>Mirrors Python's {@code UpdateMessageOperation} in
 * {@code openjiuwen/core/memory/migration/operation/operations.py}.</p>
 */
public class UpdateMessageOperation extends BaseOperation {

    private final Function<BaseMessageStore, CompletableFuture<Void>> updateFunc;

    public UpdateMessageOperation(
            OperationMetadata metadata,
            Function<BaseMessageStore, CompletableFuture<Void>> updateFunc
    ) {
        super(metadata);
        this.updateFunc = updateFunc;
    }

    public Function<BaseMessageStore, CompletableFuture<Void>> getUpdateFunc() {
        return updateFunc;
    }
}
