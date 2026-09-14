/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Message bus providing P2P and Pub-Sub routing for agent communication.
 *
 * <p>Mirrors Python's {@code MessageBus} in
 * {@code openjiuwen/core/multi_agent/team_runtime/message_bus.py}.</p>
 */
public class MessageBus {

    private static final String P2P_TOPIC_SUFFIX = "__p2p__";
    private static final String PUBSUB_TOPIC_SUFFIX = "__pubsub__";

    private final MessageBusConfig config;
    private final String teamId;
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();
    private final ReentrantLock subscriptionLock = new ReentrantLock();
    private final SubscriptionManager subscriptionManager;
    private final MessageRouter router;
    private volatile boolean running;

    public MessageBus() {
        this(new MessageBusConfig(), null);
    }

    public MessageBus(MessageBusConfig config) {
        this(config, null);
    }

    public MessageBus(MessageBusConfig config, TeamRuntime runtime) {
        this.config = config == null ? new MessageBusConfig() : config;
        this.teamId = this.config.getTeamId() == null ? "default" : this.config.getTeamId();
        this.subscriptionManager = new SubscriptionManager();
        this.router = new MessageRouter(subscriptionManager, runtime);
        Loggers.MULTI_AGENT.info("[{}] Initialized with team_id: {}", getClass().getSimpleName(), teamId);
    }

    String getP2pTopic(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return teamId + "_" + sessionId + P2P_TOPIC_SUFFIX;
        }
        return teamId + P2P_TOPIC_SUFFIX;
    }

    String getPubsubTopic(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return teamId + "_" + sessionId + PUBSUB_TOPIC_SUFFIX;
        }
        return teamId + PUBSUB_TOPIC_SUFFIX;
    }

    private void ensureSubscription(String topic) {
        if (activeSubscriptions.contains(topic)) {
            return;
        }
        subscriptionLock.lock();
        try {
            activeSubscriptions.add(topic);
        } finally {
            subscriptionLock.unlock();
        }
    }

    /**
     * Remove queue subscriptions associated with a finished session.
     *
     * @param sessionId session id
     * @return completion future
     */
    public CompletableFuture<Void> cleanupSession(String sessionId) {
        String p2pTopic = getP2pTopic(sessionId);
        String pubsubTopic = getPubsubTopic(sessionId);

        subscriptionLock.lock();
        try {
            activeSubscriptions.remove(p2pTopic);
            activeSubscriptions.remove(pubsubTopic);
        } finally {
            subscriptionLock.unlock();
        }

        Loggers.MULTI_AGENT.debug("[{}] cleanup_session: deactivated {} and {}",
                getClass().getSimpleName(), p2pTopic, pubsubTopic);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Start the message bus.
     *
     * @return completion future
     */
    public CompletableFuture<Void> start() {
        if (running) {
            Loggers.MULTI_AGENT.warning("[{}] Already running", getClass().getSimpleName());
            return CompletableFuture.completedFuture(null);
        }
        running = true;
        Loggers.MULTI_AGENT.info("[{}:{}] Started", getClass().getSimpleName(), teamId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Stop the message bus and clean up all active subscriptions.
     *
     * @return completion future
     */
    public CompletableFuture<Void> stop() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }

        Loggers.MULTI_AGENT.info("[{}:{}] Stopping...", getClass().getSimpleName(), teamId);
        running = false;
        subscriptionLock.lock();
        try {
            activeSubscriptions.clear();
        } finally {
            subscriptionLock.unlock();
        }
        Loggers.MULTI_AGENT.info("[{}:{}] Stopped", getClass().getSimpleName(), teamId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Send a P2P message and wait for the response.
     *
     * @param message message payload
     * @param recipient recipient agent id
     * @param sender optional sender id
     * @param sessionId optional session id
     * @param timeout optional timeout in seconds
     * @return response future
     */
    public CompletableFuture<Object> send(
            Object message,
            String recipient,
            String sender,
            String sessionId,
            Double timeout
    ) {
        String topic = getP2pTopic(sessionId);
        ensureSubscription(topic);

        MessageEnvelope envelope = new MessageEnvelope(
                UUID.randomUUID().toString(),
                message,
                sender,
                recipient,
                null,
                sessionId,
                Collections.emptyMap()
        );

        CompletableFuture<Object> response = router.routeP2pMessage(envelope);
        if (timeout != null && timeout > 0) {
            response = response.orTimeout((long) (timeout * 1000), TimeUnit.MILLISECONDS);
        }
        return response.exceptionallyCompose(error -> {
            if (error instanceof java.util.concurrent.TimeoutException) {
                Loggers.MULTI_AGENT.error("[{}] P2P message timeout after {}s: {} -> {}",
                        getClass().getSimpleName(), timeout, envelope.getMessageId(), recipient);
                return CompletableFuture.failedFuture(error);
            }
            String errorMsg = "Failed to get P2P message response: " + error.getMessage();
            Loggers.MULTI_AGENT.error("[{}] {}", getClass().getSimpleName(), errorMsg);
            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                    StatusCode.MESSAGE_QUEUE_MESSAGE_PROCESS_EXECUTION_ERROR,
                    "reason",
                    errorMsg
            ));
        });
    }

    /**
     * Publish a fire-and-forget message to a topic.
     *
     * @param message message payload
     * @param topicId logical topic id
     * @param sender optional sender id
     * @param sessionId optional session id
     * @return completion future
     */
    public CompletableFuture<Void> publish(Object message, String topicId, String sender, String sessionId) {
        String topic = getPubsubTopic(sessionId);
        ensureSubscription(topic);

        MessageEnvelope envelope = new MessageEnvelope(
                UUID.randomUUID().toString(),
                message,
                sender,
                null,
                topicId,
                sessionId,
                Collections.emptyMap()
        );

        return router.routePubsubMessage(envelope);
    }

    public CompletableFuture<Void> addSubscription(String agentId, String topic) {
        subscriptionManager.subscribe(agentId, topic);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> removeSubscription(String agentId, String topic) {
        subscriptionManager.unsubscribe(agentId, topic);
        return CompletableFuture.completedFuture(null);
    }

    public void removeAllSubscriptions(String agentId) {
        subscriptionManager.unsubscribeAll(agentId);
    }

    public Map<String, Object> listSubscriptions(String agentId) {
        return subscriptionManager.listSubscriptions(agentId);
    }

    public int getSubscriptionCount() {
        return subscriptionManager.getSubscriptionCount();
    }

    public MessageBusConfig getConfig() {
        return config;
    }

    public String getTeamId() {
        return teamId;
    }

    public boolean isRunning() {
        return running;
    }

    public MessageRouter getRouter() {
        return router;
    }

    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    public Set<String> getActiveSubscriptions() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(activeSubscriptions));
    }
}
