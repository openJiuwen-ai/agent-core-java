/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code StreamConsumer} in
 * {@code openjiuwen/core/graph/stream_actor/base.py}.
 */
public interface StreamConsumer {

    /**
     * Starts a stream call and counts the latch down when the consumer is ready.
     *
     * @param latch callback readiness latch
     * @param errorCallback callback receiving stream call failures
     */
    void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback);

    /**
     * Returns whether this consumer accepts stream messages now.
     *
     * @return {@code true} when messages should be handled
     */
    boolean shouldHandleMessage();

    /**
     * Returns whether the consumer is in the Python "done" state.
     *
     * @return {@code true} when a first frame may start a stream call
     */
    boolean isDone();
}
