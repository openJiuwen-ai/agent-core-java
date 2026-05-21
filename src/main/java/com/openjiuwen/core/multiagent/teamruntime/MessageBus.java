/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Message bus providing P2P and Pub-Sub routing for agent communication.
 * <p>
 * Mirrors Python's {@code MessageBus} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.message_bus}.
 * <p>
 * Supports both point-to-point and publish-subscribe patterns with
 * team/session-scoped topic isolation.
 */
public class MessageBus {
    
    private static final LoggerProtocol LOGGER = Loggers.MULTI_AGENT;
    private static final String P2P_TOPIC_SUFFIX = "__p2p__";
    private static final String PUBSUB_TOPIC_SUFFIX = "__pubsub__";
    
    private final MessageBusConfig config;
    private final String teamId;
    private final SubscriptionManager subscriptionManager;
    private final MessageRouter router;
    
    /** Active subscriptions by topic */
    private final ConcurrentHashMap<String, Object> activeSubscriptions = new ConcurrentHashMap<>();
    
    /** Lock for subscription operations */
    private final ReentrantLock subscriptionLock = new ReentrantLock();
    
    /** Running state */
    private volatile boolean running = false;
    
    /** Team runtime reference (optional) */
    private TeamRuntime runtime;
    
    public MessageBus() {
        this(new MessageBusConfig(), null);
    }
    
    public MessageBus(MessageBusConfig config) {
        this(config, null);
    }
    
    public MessageBus(MessageBusConfig config, TeamRuntime runtime) {
        this.config = config;
        this.teamId = config.getTeamId().orElse("default");
        this.subscriptionManager = new SubscriptionManager();
        this.router = new MessageRouter(subscriptionManager, runtime);
        this.runtime = runtime;
        
        LOGGER.info("[MessageBus] Initialized with team_id: {}", teamId);
    }
    
    // ========== Topic Helpers ==========
    
    /**
     * Generate the P2P topic name for a given session.
     * 
     * @param sessionId Optional session ID for per-session isolation
     * @return Topic name
     */
    private String getP2pTopic(Optional<String> sessionId) {
        if (sessionId.isPresent()) {
            return teamId + "_" + sessionId.get() + P2P_TOPIC_SUFFIX;
        }
        return teamId + P2P_TOPIC_SUFFIX;
    }
    
    /**
     * Generate the Pub-Sub topic name for a given session.
     * 
     * @param sessionId Optional session ID for per-session isolation
     * @return Topic name
     */
    private String getPubsubTopic(Optional<String> sessionId) {
        if (sessionId.isPresent()) {
            return teamId + "_" + sessionId.get() + PUBSUB_TOPIC_SUFFIX;
        }
        return teamId + PUBSUB_TOPIC_SUFFIX;
    }
    
    // ========== Lifecycle ==========
    
    /**
     * Start the message bus.
     */
    public void start() {
        if (running) {
            LOGGER.warn("[MessageBus] Already running");
            return;
        }
        
        running = true;
        LOGGER.info("[MessageBus:{}] Started", teamId);
    }
    
    /**
     * Stop the message bus and clean up all subscriptions.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        LOGGER.info("[MessageBus:{}] Stopping...", teamId);
        running = false;
        
        subscriptionLock.lock();
        try {
            activeSubscriptions.clear();
        } finally {
            subscriptionLock.unlock();
        }
        
        LOGGER.info("[MessageBus:{}] Stopped", teamId);
    }
    
    /**
     * Remove all active subscriptions created for a specific session.
     * 
     * @param sessionId The session ID whose topics should be cleaned up
     */
    public void cleanupSession(String sessionId) {
        String p2pTopic = getP2pTopic(Optional.of(sessionId));
        String pubsubTopic = getPubsubTopic(Optional.of(sessionId));
        
        subscriptionLock.lock();
        try {
            activeSubscriptions.remove(p2pTopic);
            activeSubscriptions.remove(pubsubTopic);
            LOGGER.debug("[MessageBus] cleanup_session: deactivated {} and {}", p2pTopic, pubsubTopic);
        } finally {
            subscriptionLock.unlock();
        }
    }
    
    // ========== Messaging ==========
    
    /**
     * Send a P2P message and wait for the response.
     * 
     * @param message Message payload
     * @param recipient Recipient agent ID
     * @param sender Sender agent ID (optional)
     * @param sessionId Session ID for topic isolation (optional)
     * @param timeout Response timeout in seconds (optional)
     * @return CompletableFuture with response from recipient
     */
    public CompletableFuture<Object> send(
            Object message,
            String recipient,
            Optional<String> sender,
            Optional<String> sessionId,
            Optional<Double> timeout
    ) {
        String topic = getP2pTopic(sessionId);
        
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .message(message)
                .sender(sender.orElse(null))
                .recipient(recipient)
                .sessionId(sessionId.orElse(null))
                .build();
        
        LOGGER.debug("[MessageBus] Sent to {}: {} -> {}, session={}",
                topic, sender.orElse("unknown"), recipient, sessionId.orElse("default"));
        
        // Route through router
        return router.routeP2pMessage(envelope);
    }
    
    /**
     * Publish a message to a topic (fire-and-forget).
     * 
     * @param message Message payload
     * @param topicId Topic ID (e.g., "code_events")
     * @param sender Sender agent ID (optional)
     * @param sessionId Session ID for topic isolation (optional)
     * @return CompletableFuture completed when delivery finishes
     */
    public CompletableFuture<Void> publish(
            Object message,
            String topicId,
            Optional<String> sender,
            Optional<String> sessionId
    ) {
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .message(message)
                .sender(sender.orElse(null))
                .topicId(topicId)
                .sessionId(sessionId.orElse(null))
                .build();
        
        LOGGER.debug("[MessageBus] Published to {}: from {}, session={}",
                topicId, sender.orElse("unknown"), sessionId.orElse("default"));
        
        return router.routePubsubMessage(envelope);
    }
    
    // ========== Getters ==========
    
    public String getTeamId() {
        return teamId;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }
    
    public MessageRouter getRouter() {
        return router;
    }
    
    public void setRuntime(TeamRuntime runtime) {
        this.runtime = runtime;
        this.router.setRuntime(runtime);
    }
}