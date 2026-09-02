/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stream emitter responsible for pushing stream data to the stream queue.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.emitter.StreamEmitter}.
 * 
 * @since 0.1.7
 */
public class StreamEmitter {
    /**
     * END_FRAME.
     * 
     * @since 0.1.7
     */
    public static final String END_FRAME = "all streaming outputs finish";

    private final AsyncStreamQueue streamQueue;

    /**
     * AtomicBoolean.
     * 
     * @since 0.1.7
     */
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * StreamEmitter.
     * 
     * @since 0.1.7
     */
    public StreamEmitter() {
        this.streamQueue = new AsyncStreamQueue();
    }

    /**
     * StreamEmitter.
     * 
     * @param streamQueue streamQueue
     * @since 0.1.7
     */
    public StreamEmitter(AsyncStreamQueue streamQueue) {
        this.streamQueue = streamQueue;
    }

    /**
     * getStreamQueue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AsyncStreamQueue getStreamQueue() {
        return streamQueue;
    }

    /**
     * Emit stream data.
     * 
     * @param streamData the data to emit
     * @since 0.1.7
     */
    public void emit(Object streamData) {
        if (isClosed.get()) {
            throw new IllegalStateException("Cannot emit data after the stream emitter is isClosed.");
        }
        streamQueue.send(streamData);
    }

    /**
     * isClosed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isClosed() {
        return isClosed.get();
    }

    /**
     * Close the emitter, sending END_FRAME sentinel.
     * 
     * @since 0.1.7
     */
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            if (!streamQueue.isClosed()) {
                // END_FRAME must never be dropped: consumers block forever waiting for it.
                // sendCritical applies backpressure (blocking) instead of the
                // bounded-retry-and-drop semantics used for regular data frames.
                streamQueue.sendCritical(END_FRAME);
            }
            Loggers.SESSION.debug("StreamEmitter isClosed");
        }
    }
}
