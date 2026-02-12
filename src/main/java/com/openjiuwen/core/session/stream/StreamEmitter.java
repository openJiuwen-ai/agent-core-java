/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Emits stream data to an underlying stream queue.
 * 
 * <p>Provides a simple interface for sending stream data with end-of-stream signaling.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class StreamEmitter {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    /**
     * End frame marker indicating all streaming outputs are finished.
     */
    public static final String END_FRAME = "all streaming outputs finish";
    
    private final AsyncStreamQueue streamQueue;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * Creates a new StreamEmitter.
     */
    public StreamEmitter() {
        this.streamQueue = new AsyncStreamQueue();
    }
    
    /**
     * Gets the underlying stream queue.
     * 
     * @return the stream queue
     */
    public AsyncStreamQueue getStreamQueue() {
        return streamQueue;
    }
    
    /**
     * Emits stream data.
     * 
     * @param streamData the data to emit
     * @return a CompletableFuture that completes when the data is emitted
     * @throws RuntimeException if the emitter is closed
     */
    public CompletableFuture<Void> emit(Object streamData) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("Can not emit data after the stream emitter is closed."));
        }
        return streamQueue.send(streamData);
    }
    
    /**
     * Checks if the emitter is closed.
     * 
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }
    
    /**
     * Closes the emitter.
     * 
     * @return a CompletableFuture that completes when the emitter is closed
     */
    public CompletableFuture<Void> close() {
        if (closed.getAndSet(true)) {
            logger.debug("StreamWriter is already closed.");
            return CompletableFuture.completedFuture(null);
        }
        
        if (!streamQueue.isClosed()) {
            return streamQueue.send(END_FRAME);
        }
        
        return CompletableFuture.completedFuture(null);
    }
}

