/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import java.util.concurrent.CompletableFuture;

/**
 * Message for invoke (request-response) pattern.
 * Mirrors Python's {@code InvokeQueueMessage}.
 */
public class InvokeQueueMessage extends QueueMessage {

    private final CompletableFuture<Object> response = new CompletableFuture<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public InvokeQueueMessage() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public InvokeQueueMessage(String messageId, Object payload) {
        super(messageId, payload);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Object> getResponse() {
        return response;
    }
}
