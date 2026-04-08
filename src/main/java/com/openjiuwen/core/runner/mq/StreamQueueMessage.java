/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Message for streaming (iterator) pattern.
 * Mirrors Python's {@code StreamQueueMessage}.
 */
public class StreamQueueMessage extends QueueMessage {

    private final CompletableFuture<Iterator<Object>> response = new CompletableFuture<>();

    public StreamQueueMessage() {
    }

    public StreamQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
    }

    public CompletableFuture<Iterator<Object>> getResponse() {
        return response;
    }
}
