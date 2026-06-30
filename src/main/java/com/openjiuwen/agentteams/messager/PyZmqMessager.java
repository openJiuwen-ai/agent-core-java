/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PyZmqMessager implements Messager {

    private static final Map<String, Map<String, MessagerHandler>> TOPIC_SUBS = new ConcurrentHashMap<>();
    private static final Map<String, MessagerHandler> P2P_HANDLERS = new ConcurrentHashMap<>();

    private final MessagerTransportConfig config;
    private final List<String> subscribedTopics = new ArrayList<>();

    public PyZmqMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : MessagerTransportConfig.builder().backend("pyzmq").build();
    }

    public static void cleanupGlobalState() {
        TOPIC_SUBS.clear();
        P2P_HANDLERS.clear();
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        for (String topic : List.copyOf(subscribedTopics)) {
            unsubscribe(topic);
        }
        unregisterDirectMessageHandler();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> publish(String topicId, EventMessage message) {
        EventMessage effectiveMessage = message;
        if ((effectiveMessage.getSenderId() == null || effectiveMessage.getSenderId().isBlank())
                && config.getNodeId() != null) {
            effectiveMessage = effectiveMessage.toBuilder().senderId(config.getNodeId()).build();
        }
        final EventMessage finalMessage = effectiveMessage;
        Map<String, MessagerHandler> subs = TOPIC_SUBS.get(topicId);
        if (subs == null || subs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> futures = subs.values().stream()
                .map(handler -> handler.handle(finalMessage).exceptionally(ignored -> null))
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> subscribe(String topicId, MessagerHandler handler) {
        TOPIC_SUBS.computeIfAbsent(topicId, ignored -> new ConcurrentHashMap<>())
                .put(config.getNodeId(), handler);
        subscribedTopics.add(topicId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unsubscribe(String topicId) {
        Map<String, MessagerHandler> subs = TOPIC_SUBS.get(topicId);
        if (subs != null) {
            subs.remove(config.getNodeId());
            if (subs.isEmpty()) {
                TOPIC_SUBS.remove(topicId);
            }
        }
        subscribedTopics.remove(topicId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> send(String agentId, EventMessage message) {
        MessagerHandler handler = P2P_HANDLERS.get(agentId);
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }
        EventMessage effectiveMessage = message;
        if ((effectiveMessage.getSenderId() == null || effectiveMessage.getSenderId().isBlank())
                && config.getNodeId() != null) {
            effectiveMessage = effectiveMessage.toBuilder().senderId(config.getNodeId()).build();
        }
        return handler.handle(effectiveMessage);
    }

    @Override
    public CompletableFuture<Map<String, Object>> sendAndWait(
            String agentId, Map<String, Object> payload, Duration timeout) {
        String requestId = UUID.randomUUID().toString();
        String replyTo = config.getNodeId() + ":reply:" + requestId;
        CompletableFuture<Map<String, Object>> response = new CompletableFuture<>();
        P2P_HANDLERS.put(replyTo, message -> {
            response.complete(message.getPayload());
            P2P_HANDLERS.remove(replyTo);
            return CompletableFuture.completedFuture(null);
        });
        Map<String, Object> requestPayload = new LinkedHashMap<>(payload != null ? payload : Map.of());
        requestPayload.put("reply_to", replyTo);
        requestPayload.put("request_id", requestId);
        EventMessage request = EventMessage.builder().eventType("request").payload(requestPayload).build();
        send(agentId, request).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                P2P_HANDLERS.remove(replyTo);
                response.completeExceptionally(throwable);
            }
        });
        Duration effectiveTimeout = timeout != null ? timeout : Duration.ofSeconds(30);
        response.orTimeout(effectiveTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((ignored, throwable) -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        P2P_HANDLERS.remove(replyTo);
                    }
                });
        return response;
    }

    @Override
    public CompletableFuture<Void> registerDirectMessageHandler(MessagerHandler handler) {
        P2P_HANDLERS.put(config.getNodeId(), handler);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterDirectMessageHandler() {
        P2P_HANDLERS.remove(config.getNodeId());
        return CompletableFuture.completedFuture(null);
    }
}
