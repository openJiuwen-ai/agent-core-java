/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.CompletableFuture;

/**
 * Message for invoke (request-response) pattern.
 *
 * <p>Mirrors Python's {@code InvokeQueueMessage} in
 * {@code openjiuwen/core/runner/message_queue_base.py}.
 */
public class InvokeQueueMessage extends QueueMessage {

    @JsonIgnore
    private final CompletableFuture<Object> response = new CompletableFuture<>();

    public InvokeQueueMessage() {
    }

    public InvokeQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
    }

    public CompletableFuture<Object> getResponse() {
        return response;
    }
}
