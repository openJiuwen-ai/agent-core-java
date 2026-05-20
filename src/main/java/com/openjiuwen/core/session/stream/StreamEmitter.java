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
 */
public class StreamEmitter {

    /**
     * Sentinel value indicating end of stream.
     */
    public static final String END_FRAME = "all streaming outputs finish";

    private final AsyncStreamQueue streamQueue;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamEmitter() {
        this.streamQueue = new AsyncStreamQueue();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamEmitter(AsyncStreamQueue streamQueue) {
        this.streamQueue = streamQueue;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AsyncStreamQueue getStreamQueue() {
        return streamQueue;
    }

    /**
     * Emit stream data.
     *
     * @param streamData the data to emit
     */
    public void emit(Object streamData) {
        if (isClosed.get()) {
            throw new IllegalStateException("Cannot emit data after the stream emitter is isClosed.");
        }
        streamQueue.send(streamData);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isClosed() {
        return isClosed.get();
    }

    /**
     * Close the emitter, sending END_FRAME sentinel.
     */
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            if (!streamQueue.isClosed()) {
                streamQueue.send(END_FRAME);
            }
            Loggers.SESSION.debug("StreamEmitter isClosed");
        }
    }
}
