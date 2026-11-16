/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

/**
 * Stream emitter that pushes data into {@link AsyncStreamQueue}.
 * <p>
 * Mirrors Python's {@code StreamEmitter} in
 * {@code openjiuwen/core/session/stream/emitter.py}.
 */
public class StreamEmitter {

    public static final String END_FRAME = "all streaming outputs finish";

    private final AsyncStreamQueue streamQueue;
    private boolean closed;

    public StreamEmitter() {
        this(new AsyncStreamQueue());
    }

    public StreamEmitter(AsyncStreamQueue streamQueue) {
        this.streamQueue = streamQueue;
    }

    public AsyncStreamQueue getStreamQueue() {
        return streamQueue;
    }

    public void emit(Object streamData) {
        if (closed) {
            throw new RuntimeException("Can not emit data after the stream emitter is closed.");
        }
        streamQueue.send(streamData);
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!streamQueue.isClosed()) {
            // END_FRAME is sent with a bounded total wait instead of the
            // bounded-retry-and-drop semantics used for regular data frames;
            // if the queue stays full past the critical send budget, the
            // oldest data frames are evicted just enough to admit it (a
            // further failure is logged).
            streamQueue.sendCritical(END_FRAME);
        }
    }
}
