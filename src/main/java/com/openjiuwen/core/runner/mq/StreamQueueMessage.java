/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamQueueMessage() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Iterator<Object>> getResponse() {
        return response;
    }
}
