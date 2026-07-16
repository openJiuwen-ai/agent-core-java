/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for asynchronous message handlers.
 * <p>
 * Mirrors Python's {@code AsyncMessageHandler = Callable[[Any], Awaitable[Any]]}
 * from {@code message_queue_base.py}.
 * <p>
 * This interface represents a handler that processes a message asynchronously
 * and returns a future result. In Java, async operations use {@link CompletableFuture}
 * instead of Python's {@code asyncio.Future}.
 * <p>
 * Usage example:
 * 
 * <pre>
 * AsyncMessageHandler handler = message -> {
 *     return CompletableFuture.supplyAsync(() -> {
 *         // Process message
 *         return processedResult;
 *     });
 * };
 * </pre>
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface AsyncMessageHandler<T, R> {
    /**
     * handle.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<R> handle(T message);
}
