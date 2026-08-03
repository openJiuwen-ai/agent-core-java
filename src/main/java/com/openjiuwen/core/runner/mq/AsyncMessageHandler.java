/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for asynchronous message handlers.
 *
 * <p>Mirrors Python's {@code AsyncMessageHandler = Callable[[Any], Awaitable[Any]]}
 * in {@code openjiuwen/core/runner/message_queue_base.py}.
 *
 * @param <T> input message type
 * @param <R> handler result type
 */
@FunctionalInterface
public interface AsyncMessageHandler<T, R> {

    CompletableFuture<R> handle(T message);
}
