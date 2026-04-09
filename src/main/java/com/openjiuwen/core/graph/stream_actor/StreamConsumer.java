/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.graph.stream_actor;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Interface for graph nodes that can consume streaming data.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamConsumer}.
 */
public interface StreamConsumer {

    /**
     * Handle stream-in call for stream abilities (COLLECT, TRANSFORM).
     *
     * @param latch         a latch to signal when stream-in is ready
     * @param errorCallback callback for error reporting
     */
    void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback);

    /**
     * Whether this consumer should handle stream messages.
     *
     * @return true if it has stream abilities (COLLECT/TRANSFORM)
     */
    boolean shouldHandleMessage();

    /**
     * Whether this consumer has completed its execution cycle.
     *
     * @return true if done
     */
    boolean isDone();
}
