/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Message for streaming (iterator) pattern.
 *
 * <p>Mirrors Python's {@code StreamQueueMessage} in
 * {@code openjiuwen/core/runner/message_queue_base.py}.
 */
public class StreamQueueMessage extends QueueMessage {

    @JsonIgnore
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
