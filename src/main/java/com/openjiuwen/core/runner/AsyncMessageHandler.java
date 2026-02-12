// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Functional interface for async message handling.
 */
@FunctionalInterface
public interface AsyncMessageHandler extends Function<Object, CompletableFuture<Object>> {
    
    /**
     * Handles a message asynchronously.
     *
     * @param message the message payload to handle
     * @return a future containing the result
     */
    @Override
    CompletableFuture<Object> apply(Object message);
}

