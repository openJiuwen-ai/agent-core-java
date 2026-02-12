// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.concurrent.CompletableFuture;

/**
 * Local message queue interface for in-process communication.
 */
public class LocalMessageQueue {
    
    /**
     * Starts the message queue.
     *
     * @return a future that completes when the queue is started
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Stops the message queue.
     *
     * @return a future that completes when the queue is stopped
     */
    public CompletableFuture<Void> stop() {
        return CompletableFuture.completedFuture(null);
    }
}

