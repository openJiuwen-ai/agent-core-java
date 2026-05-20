/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Team messaging abstraction for topic pub/sub and direct peer messaging.
 *
 * @since 1.0
 */
public interface Messager {
    CompletableFuture<Void> start();
    CompletableFuture<Void> stop();
    CompletableFuture<Void> publish(String topicId, EventMessage message);
    CompletableFuture<Void> subscribe(String topicId, MessagerHandler handler);
    CompletableFuture<Void> unsubscribe(String topicId);
    CompletableFuture<Void> send(String agentId, EventMessage message);
    default CompletableFuture<Map<String, Object>> sendAndWait(
            String agentId,
            Map<String, Object> payload,
            Duration timeout
    ) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("sendAndWait is not supported"));
        return future;
    }
    CompletableFuture<Void> registerDirectMessageHandler(MessagerHandler handler);
    CompletableFuture<Void> unregisterDirectMessageHandler();
}
