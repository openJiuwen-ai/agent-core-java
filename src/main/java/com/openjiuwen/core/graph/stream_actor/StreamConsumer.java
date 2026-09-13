/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Interface for graph nodes that can consume streaming data.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamConsumer}.
 * 
 * @since 0.1.7
 */
public interface StreamConsumer {
    /**
     * streamCall.
     * 
     * @param latch latch
     * @param errorCallback errorCallback
     * @since 0.1.7
     */
    void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback);

    /**
     * Whether this consumer should handle stream messages.
     * 
     * @return true if it has stream abilities (COLLECT/TRANSFORM)
     * @since 0.1.7
     */
    boolean shouldHandleMessage();

    /**
     * Whether this consumer has completed its execution cycle.
     * 
     * @return true if done
     * @since 0.1.7
     */
    boolean isDone();
}
