/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * In-process messager using direct handler callbacks.
 * <p>
 * Mirrors Python's {@code InProcessMessager} in
 * {@code openjiuwen/agent_teams/messager/inprocess.py}.
 */
public class InProcessMessager implements Messager {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final Bus BUS = new Bus();

    private final MessagerTransportConfig config;
    private final List<String> subscribedTopics = new ArrayList<>();

    public InProcessMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : new MessagerTransportConfig();
    }

    public static void cleanupInprocessBus() {
        BUS.clear();
    }

    @Override
    public CompletionStage<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> stop() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> publish(String topicId, EventMessage message) {
        EventMessage outgoing = stampSenderIfMissing(message);
        return BUS.publish(localAgentId(), topicId, outgoing);
    }

    @Override
    public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
        BUS.subscribe(localAgentId(), topicId, handler);
        subscribedTopics.add(topicId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unsubscribe(String topicId) {
        BUS.unsubscribe(localAgentId(), topicId);
        subscribedTopics.remove(topicId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> send(String agentId, EventMessage message) {
        return BUS.send(agentId, message);
    }

    @Override
    public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
        BUS.registerP2p(localAgentId(), handler);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unregisterDirectMessageHandler() {
        BUS.unregisterP2p(localAgentId());
        return CompletableFuture.completedFuture(null);
    }

    private String localAgentId() {
        return config.getNodeId() != null ? config.getNodeId() : "";
    }

    private EventMessage stampSenderIfMissing(EventMessage message) {
        if (message == null) {
            return null;
        }
        if (message.getSenderId() != null && !message.getSenderId().isBlank()) {
            return message;
        }
        return new EventMessage(
                message.getEventType(),
                message.getPayloadData() != null ? new LinkedHashMap<>(message.getPayloadData()) : new LinkedHashMap<>(),
                localAgentId()
        );
    }

    private static final class Bus {

        private final Map<String, Map<String, MessagerHandler>> topicSubscriptions = new LinkedHashMap<>();
        private final Map<String, MessagerHandler> p2pHandlers = new LinkedHashMap<>();

        CompletionStage<Void> publish(String publisherId, String topic, EventMessage message) {
            Map<String, MessagerHandler> handlers;
            synchronized (this) {
                handlers = topicSubscriptions.get(topic) != null
                        ? new LinkedHashMap<>(topicSubscriptions.get(topic))
                        : Map.of();
            }
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (Map.Entry<String, MessagerHandler> entry : handlers.entrySet()) {
                String agentId = entry.getKey();
                MessagerHandler handler = entry.getValue();
                chain = chain.thenCompose(ignored ->
                        invokeQuietly(handler, message, () ->
                                TEAM_LOGGER.error(
                                        "[_Bus] publish to {} on topic {} failed",
                                        agentId,
                                        topic
                                )));
            }
            return chain;
        }

        synchronized void subscribe(String agentId, String topic, MessagerHandler handler) {
            topicSubscriptions.computeIfAbsent(topic, ignored -> new LinkedHashMap<>()).put(agentId, handler);
        }

        synchronized void unsubscribe(String agentId, String topic) {
            Map<String, MessagerHandler> handlers = topicSubscriptions.get(topic);
            if (handlers == null) {
                return;
            }
            handlers.remove(agentId);
            if (handlers.isEmpty()) {
                topicSubscriptions.remove(topic);
            }
        }

        CompletionStage<Void> send(String agentId, EventMessage message) {
            MessagerHandler handler;
            synchronized (this) {
                handler = p2pHandlers.get(agentId);
            }
            if (handler == null) {
                TEAM_LOGGER.warning("[_Bus] no P2P handler for agent_id={}", agentId);
                return CompletableFuture.completedFuture(null);
            }
            return invokeQuietly(handler, message, () ->
                    TEAM_LOGGER.error("[_Bus] direct send to {} failed", agentId));
        }

        synchronized void registerP2p(String agentId, MessagerHandler handler) {
            p2pHandlers.put(agentId, handler);
        }

        synchronized void unregisterP2p(String agentId) {
            p2pHandlers.remove(agentId);
        }

        synchronized void clear() {
            topicSubscriptions.clear();
            p2pHandlers.clear();
        }

        private CompletionStage<Void> invokeQuietly(
                MessagerHandler handler,
                EventMessage message,
                Runnable onFailure
        ) {
            try {
                return handler.handle(message)
                        .handle((ignored, error) -> {
                            if (error != null) {
                                onFailure.run();
                            }
                            return null;
                        });
            } catch (RuntimeException error) {
                onFailure.run();
                return CompletableFuture.completedFuture(null);
            }
        }
    }
}
