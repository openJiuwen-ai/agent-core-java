/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import java.util.concurrent.CompletableFuture;

/**
 * Message for invoke (request-response) pattern.
 * Mirrors Python's {@code InvokeQueueMessage}.
 * 
 * @since 0.1.7
 */
public class InvokeQueueMessage extends QueueMessage {
    private final CompletableFuture<Object> response = new CompletableFuture<>();

    /**
     * InvokeQueueMessage.
     * 
     * @since 0.1.7
     */
    public InvokeQueueMessage() {
    }

    /**
     * InvokeQueueMessage.
     * 
     * @param messageId messageId
     * @param payload payload
     * @since 0.1.7
     */
    public InvokeQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
    }

    /**
     * getResponse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Object> getResponse() {
        return response;
    }
}
