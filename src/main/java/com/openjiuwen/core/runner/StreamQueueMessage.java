// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.concurrent.CompletableFuture;
import java.util.Iterator;

/**
 * Queue message for streaming pattern.
 *
 * @param <T> the type of the stream elements
 */
public class StreamQueueMessage<T> extends QueueMessage {
    
    private final CompletableFuture<Iterator<T>> response;
    
    public StreamQueueMessage() {
        this.response = new CompletableFuture<>();
    }
    
    public StreamQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
        this.response = new CompletableFuture<>();
    }
    
    public CompletableFuture<Iterator<T>> getResponse() {
        return response;
    }
}

