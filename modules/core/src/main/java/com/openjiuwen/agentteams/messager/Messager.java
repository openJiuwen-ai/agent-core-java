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
 * @since 0.1.7
 */
public interface Messager {
    /**
     * start.
     * 
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> start();

    /**
     * stop.
     * 
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> stop();

    /**
     * publish.
     * 
     * @param topicId topicId
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> publish(String topicId, EventMessage message);

    /**
     * subscribe.
     * 
     * @param topicId topicId
     * @param handler handler
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> subscribe(String topicId, MessagerHandler handler);

    /**
     * unsubscribe.
     * 
     * @param topicId topicId
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> unsubscribe(String topicId);

    /**
     * send.
     * 
     * @param agentId agentId
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> send(String agentId, EventMessage message);

    /**
     * sendAndWait.
     * 
     * @param agentId agentId
     * @param payload payload
     * @param timeout timeout
     * @return the result
     * @since 0.1.7
     */
    default CompletableFuture<Map<String, Object>> sendAndWait(String agentId, Map<String, Object> payload,
            Duration timeout) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("sendAndWait is not supported"));
        return future;
    }

    /**
     * registerDirectMessageHandler.
     * 
     * @param handler handler
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> registerDirectMessageHandler(MessagerHandler handler);

    /**
     * unregisterDirectMessageHandler.
     * 
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> unregisterDirectMessageHandler();
}
