// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.concurrent.CompletableFuture;

/**
 * Queue message for invoke (request-response) pattern.
 *
 * @param <T> the type of the response
 */
public class InvokeQueueMessage<T> extends QueueMessage {
    
    private final CompletableFuture<T> response;
    
    public InvokeQueueMessage() {
        this.response = new CompletableFuture<>();
    }
    
    public InvokeQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
        this.response = new CompletableFuture<>();
    }
    
    public CompletableFuture<T> getResponse() {
        return response;
    }
}

