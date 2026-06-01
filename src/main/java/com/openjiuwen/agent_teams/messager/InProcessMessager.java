/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-local messager transport with pub-sub and direct-message delivery.
 *
 * <p>Mirrors Python's {@code InProcessMessager} in
 * {@code openjiuwen.agent_teams.messager.inprocess}.</p>
 */
public class InProcessMessager implements Messager {

    private static final Bus BUS = new Bus();

    private final MessagerTransportConfig config;

    public InProcessMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : new MessagerTransportConfig();
    }

    @Override
    public void start() {
        // no-op for in-process transport
    }

    @Override
    public void stop() {
        unregisterDirectMessageHandler();
    }

    @Override
    public void publish(String topicId, EventMessage message) {
        stampSender(message);
        BUS.publish(topicId, message);
    }

    @Override
    public void subscribe(String topicId, MessagerHandler handler) {
        BUS.subscribe(config.getNodeId(), topicId, handler);
    }

    @Override
    public void unsubscribe(String topicId) {
        BUS.unsubscribe(config.getNodeId(), topicId);
    }

    @Override
    public void send(String agentId, EventMessage message) {
        stampSender(message);
        BUS.send(agentId, message);
    }

    @Override
    public void registerDirectMessageHandler(MessagerHandler handler) {
        BUS.registerP2p(config.getNodeId(), handler);
    }

    @Override
    public void unregisterDirectMessageHandler() {
        BUS.unregisterP2p(config.getNodeId());
    }

    public static void cleanupBus() {
        BUS.clear();
    }

    private void stampSender(EventMessage message) {
        if (message != null && (message.getSenderId() == null || message.getSenderId().isBlank())) {
            message.setSenderId(config.getNodeId());
        }
    }

    private static final class Bus {
        private final Map<String, Map<String, MessagerHandler>> topicSubscriptions = new LinkedHashMap<>();
        private final Map<String, MessagerHandler> p2pHandlers = new LinkedHashMap<>();

        void subscribe(String agentId, String topic, MessagerHandler handler) {
            if (agentId == null || agentId.isBlank() || topic == null || handler == null) {
                return;
            }
            topicSubscriptions.computeIfAbsent(topic, ignored -> new LinkedHashMap<>()).put(agentId, handler);
        }

        void unsubscribe(String agentId, String topic) {
            Map<String, MessagerHandler> handlers = topicSubscriptions.get(topic);
            if (handlers == null) {
                return;
            }
            handlers.remove(agentId);
            if (handlers.isEmpty()) {
                topicSubscriptions.remove(topic);
            }
        }

        void publish(String topic, EventMessage message) {
            Map<String, MessagerHandler> handlers = topicSubscriptions.get(topic);
            if (handlers == null) {
                return;
            }
            for (MessagerHandler handler : handlers.values()) {
                try {
                    handler.handle(message);
                } catch (RuntimeException ignored) {
                    // Python logs a failing subscriber and continues fan-out.
                }
            }
        }

        void registerP2p(String agentId, MessagerHandler handler) {
            if (agentId != null && !agentId.isBlank() && handler != null) {
                p2pHandlers.put(agentId, handler);
            }
        }

        void unregisterP2p(String agentId) {
            if (agentId != null) {
                p2pHandlers.remove(agentId);
            }
        }

        void send(String agentId, EventMessage message) {
            MessagerHandler handler = p2pHandlers.get(agentId);
            if (handler != null) {
                handler.handle(message);
            }
        }

        void clear() {
            topicSubscriptions.clear();
            p2pHandlers.clear();
        }
    }
}
