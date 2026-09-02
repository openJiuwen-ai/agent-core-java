/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Message for streaming (iterator) pattern.
 * Mirrors Python's {@code StreamQueueMessage}.
 * 
 * @since 0.1.7
 */
public class StreamQueueMessage extends QueueMessage {
    private final CompletableFuture<Iterator<Object>> response = new CompletableFuture<>();

    /**
     * StreamQueueMessage.
     * 
     * @since 0.1.7
     */
    public StreamQueueMessage() {
    }

    /**
     * StreamQueueMessage.
     * 
     * @param messageId messageId
     * @param payload payload
     * @since 0.1.7
     */
    public StreamQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
    }

    /**
     * getResponse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Iterator<Object>> getResponse() {
        return response;
    }
}
