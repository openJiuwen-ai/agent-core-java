/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Self-contained runtime for multi-agent communication.
 * <p>
 * Mirrors Python's {@code TeamRuntime} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.team_runtime}.
 * <p>
 * Manages agent registration, routes P2P and Pub-Sub messages.
 */
public class TeamRuntime {
    
    private final RuntimeConfig config;
    private final String teamId;
    private final SubscriptionManager subscriptionManager;
    
    /** Agent card registry */
    private final ConcurrentHashMap<String, AgentCard> agentCards = new ConcurrentHashMap<>();
    
    /** Agent provider registry */
    private final ConcurrentHashMap<String, Supplier<?>> agentProviders = new ConcurrentHashMap<>();

    /** Active standalone team sessions, keyed by session ID. */
    private final ConcurrentHashMap<String, Session> activeTeamSessions = new ConcurrentHashMap<>();
    
    /** P2P timeout in seconds */
    private double p2pTimeout;
    
    public TeamRuntime() {
        this(new RuntimeConfig());
    }
    
    public TeamRuntime(RuntimeConfig config) {
        this.config = config;
        this.teamId = config.getTeamId();
        this.subscriptionManager = new SubscriptionManager();
        this.p2pTimeout = config.getP2pTimeout();
    }
    
    /**
     * Register an agent with its card and provider.
     * 
     * @param card AgentCard
     * @param provider Supplier that creates agent instance
     */
    public void registerAgent(AgentCard card, Supplier<?> provider) {
        agentCards.put(card.getId(), card);
        agentProviders.put(card.getId(), provider);
    }
    
    /**
     * Check if an agent is registered.
     * 
     * @param agentId Agent ID
     * @return true if registered
     */
    public boolean hasAgent(String agentId) {
        return agentCards.containsKey(agentId);
    }
    
    /**
     * Get an agent card by ID.
     * 
     * @param agentId Agent ID
     * @return AgentCard or null
     */
    public AgentCard getAgentCard(String agentId) {
        return agentCards.get(agentId);
    }
    
    /**
     * Get all registered agent IDs.
     * 
     * @return Set of agent IDs
     */
    public Set<String> getAgentIds() {
        return agentCards.keySet();
    }
    
    /**
     * Set P2P timeout.
     * 
     * @param timeout Timeout in seconds
     */
    public void setP2pTimeout(double timeout) {
        this.p2pTimeout = timeout;
    }
    
    /**
     * Get subscription manager.
     * 
     * @return SubscriptionManager
     */
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }
    
    /**
     * Get message bus (creates one if needed).
     * 
     * @return MessageBus
     */
    public MessageBus getMessageBus() {
        // Lazy create message bus if needed
        if (messageBus == null) {
            MessageBusConfig busConfig = MessageBusConfig.builder()
                    .teamId(teamId)
                    .build();
            messageBus = new MessageBus(busConfig, this);
        }
        return messageBus;
    }
    
    private MessageBus messageBus;
    
    /**
     * Get team ID.
     * 
     * @return team ID
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * Clean up all message-bus state for a finished session.
     *
     * @param sessionId session ID
     */
    public void cleanupSession(String sessionId) {
        if (sessionId != null) {
            getMessageBus().cleanupSession(sessionId);
        }
    }

    /**
     * Bind an active standalone team session to this runtime.
     *
     * @param session session to bind
     */
    public void bindTeamSession(Session session) {
        if (session != null && session.getSessionId() != null) {
            activeTeamSessions.put(session.getSessionId(), session);
        }
    }

    /**
     * Remove a standalone team session binding.
     *
     * @param sessionId session ID
     */
    public void unbindTeamSession(String sessionId) {
        if (sessionId != null) {
            activeTeamSessions.remove(sessionId);
        }
    }

    /**
     * Get an active standalone team session.
     *
     * @param sessionId session ID
     * @return bound session, or null
     */
    public Session getTeamSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return activeTeamSessions.get(sessionId);
    }

    /**
     * Start the runtime message bus.
     */
    public void start() {
        getMessageBus().start();
    }

    /**
     * Stop the runtime message bus.
     */
    public void stop() {
        getMessageBus().stop();
    }

    /**
     * Send a P2P message through the runtime.
     *
     * @param message message payload
     * @param recipient recipient agent ID
     * @param sender sender agent ID
     * @return response future
     */
    public CompletableFuture<Object> send(Object message, String recipient, String sender) {
        return send(message, recipient, sender, null);
    }

    /**
     * Send a P2P message through the runtime with session isolation.
     *
     * @param message message payload
     * @param recipient recipient agent ID
     * @param sender sender agent ID
     * @param sessionId optional session ID
     * @return response future
     */
    public CompletableFuture<Object> send(Object message, String recipient, String sender, String sessionId) {
        return getMessageBus().send(message, recipient, Optional.ofNullable(sender),
                Optional.ofNullable(sessionId), Optional.of(p2pTimeout));
    }

    /**
     * Publish a message to a topic.
     *
     * @param message message payload
     * @param topicId topic ID
     * @param sender sender agent ID
     * @return completion future
     */
    public CompletableFuture<Void> publish(Object message, String topicId, String sender) {
        return publish(message, topicId, sender, null);
    }

    /**
     * Publish a message to a topic with session isolation.
     *
     * @param message message payload
     * @param topicId topic ID
     * @param sender sender agent ID
     * @param sessionId optional session ID
     * @return completion future
     */
    public CompletableFuture<Void> publish(Object message, String topicId, String sender, String sessionId) {
        return getMessageBus().publish(message, topicId, Optional.ofNullable(sender),
                Optional.ofNullable(sessionId));
    }

    /**
     * Subscribe an agent to a topic pattern.
     *
     * @param agentId agent ID
     * @param topicPattern topic pattern
     */
    public void subscribe(String agentId, String topicPattern) {
        subscriptionManager.subscribe(agentId, topicPattern);
    }

    Object dispatchToAgent(String agentId, Object message, String sessionId) {
        Object agent = resolveAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + agentId);
        }
        if (agent instanceof CommunicableAgent communicableAgent) {
            communicableAgent.bindRuntime(this, agentId);
        }
        Session session = getTeamSession(sessionId);
        if (agent instanceof BaseAgent baseAgent) {
            return baseAgent.invoke(message, session);
        }
        if (agent instanceof Function<?, ?> function) {
            @SuppressWarnings("unchecked")
            Function<Object, Object> typedFunction = (Function<Object, Object>) function;
            return typedFunction.apply(message);
        }
        return invokeReflectively(agent, message, session);
    }

    private Object resolveAgent(String agentId) {
        Supplier<?> provider = agentProviders.get(agentId);
        return provider != null ? provider.get() : null;
    }

    private Object invokeReflectively(Object agent, Object message, Session session) {
        try {
            Method method = agent.getClass().getMethod("invoke", Object.class, Session.class);
            return method.invoke(agent, message, session);
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = agent.getClass().getMethod("invoke", Object.class);
                return method.invoke(agent, message);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Agent does not expose an invoke method: "
                        + agent.getClass().getName(), e);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke agent: " + agent.getClass().getName(), e);
        }
    }
    
    /**
     * Unregister an agent.
     * 
     * @param agentId Agent ID
     */
    public void unregisterAgent(String agentId) {
        agentCards.remove(agentId);
        agentProviders.remove(agentId);
        subscriptionManager.unsubscribeAll(agentId);
    }
}
