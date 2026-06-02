/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
    private final MessageBus messageBus;
    
    /** Agent card registry */
    private final ConcurrentHashMap<String, AgentCard> agentCards = new ConcurrentHashMap<>();
    
    /** Agent provider registry */
    private final ConcurrentHashMap<String, Supplier<?>> agentProviders = new ConcurrentHashMap<>();

    /** Active standalone team sessions, keyed by session ID. */
    private final ConcurrentHashMap<String, Session> activeTeamSessions = new ConcurrentHashMap<>();
    
    /** P2P timeout in seconds */
    private double p2pTimeout;

    /** Running state. */
    private volatile boolean running;
    
    public TeamRuntime() {
        this(new RuntimeConfig());
    }
    
    public TeamRuntime(RuntimeConfig config) {
        this.config = config;
        this.teamId = config.getTeamId();
        this.subscriptionManager = new SubscriptionManager();
        this.p2pTimeout = config.getP2pTimeout();
        MessageBusConfig busConfig = config.getMessageBus().orElseGet(MessageBusConfig::new);
        busConfig.setTeamId(teamId);
        this.messageBus = new MessageBus(busConfig, this);
        this.running = false;
    }
    
    /**
     * Register an agent with its card and provider.
     * 
     * @param card AgentCard
     * @param provider Supplier that creates agent instance
     */
    public void registerAgent(AgentCard card, Supplier<?> provider) {
        String agentId = card.getId();
        agentCards.put(agentId, card);
        agentProviders.put(agentId, wrapProvider(provider, agentId));
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
     * Get the number of registered agents.
     *
     * @return agent count
     */
    public int getAgentCount() {
        return agentCards.size();
    }

    /**
     * List all registered agent IDs.
     *
     * @return list of agent IDs
     */
    public List<String> listAgents() {
        return new ArrayList<>(agentCards.keySet());
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
     * Get P2P timeout.
     *
     * @return timeout in seconds
     */
    public double getP2pTimeout() {
        return p2pTimeout;
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
        return messageBus;
    }
    
    /**
     * Get team ID.
     * 
     * @return team ID
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * Check whether the runtime is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
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
        if (running) {
            return;
        }
        getMessageBus().start();
        running = true;
    }

    /**
     * Stop the runtime message bus.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
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
     * Send a P2P message through the runtime with an explicit timeout.
     *
     * @param message message payload
     * @param recipient recipient agent ID
     * @param sender sender agent ID
     * @param sessionId optional session ID
     * @param timeout optional timeout in seconds
     * @return response future
     */
    public CompletableFuture<Object> send(Object message, String recipient, String sender, String sessionId, Double timeout) {
        validateSendInputs(recipient, sender);
        if (!hasAgent(recipient)) {
            throw new IllegalArgumentException("Recipient '" + recipient + "' not registered in runtime");
        }
        ensureStarted();
        return getMessageBus().send(message, recipient, Optional.ofNullable(sender),
                Optional.ofNullable(sessionId), Optional.ofNullable(timeout != null ? timeout : p2pTimeout));
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
        return send(message, recipient, sender, sessionId, p2pTimeout);
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
        validatePublishInputs(topicId, sender);
        ensureStarted();
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
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required for subscription");
        }
        if (topicPattern == null || topicPattern.isBlank()) {
            throw new IllegalArgumentException("topic is required for subscription");
        }
        subscriptionManager.subscribe(agentId, topicPattern);
    }

    /**
     * Unsubscribe an agent from a topic pattern.
     *
     * @param agentId agent ID
     * @param topicPattern topic pattern
     */
    public void unsubscribe(String agentId, String topicPattern) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required for unsubscription");
        }
        if (topicPattern == null || topicPattern.isBlank()) {
            throw new IllegalArgumentException("topic is required for unsubscription");
        }
        subscriptionManager.unsubscribe(agentId, topicPattern);
    }

    /**
     * List subscriptions for debugging and introspection.
     *
     * @param agentId optional agent ID filter
     * @return subscription snapshot
     */
    public java.util.Map<String, Object> listSubscriptions(String agentId) {
        return getMessageBus().listSubscriptions(agentId);
    }

    /**
     * List subscriptions for debugging and introspection.
     *
     * @return subscription snapshot
     */
    public java.util.Map<String, Object> listSubscriptions() {
        return listSubscriptions(null);
    }

    /**
     * Return the total number of topic subscriptions.
     *
     * @return subscription count
     */
    public int getSubscriptionCount() {
        return getMessageBus().getSubscriptionCount();
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
        return invokeReflectively(agent, message, session, sessionId);
    }

    /**
     * Create a fresh agent instance from its registered provider.
     *
     * @param agentId agent ID
     * @return new agent instance or null
     */
    public Object createAgent(String agentId) {
        return resolveAgent(agentId);
    }

    private Object resolveAgent(String agentId) {
        Supplier<?> provider = agentProviders.get(agentId);
        return provider != null ? provider.get() : null;
    }

    private Object invokeReflectively(Object agent, Object message, Session session, String sessionId) {
        try {
            Method method = findAccessibleMethod(agent.getClass(), "invoke", Object.class, Session.class);
            return method.invoke(agent, message, session);
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = findAccessibleMethod(agent.getClass(), "invoke", Object.class, String.class);
                return method.invoke(agent, message, session != null ? session.getSessionId() : sessionId);
            } catch (NoSuchMethodException e) {
                try {
                    Method method = findAccessibleMethod(agent.getClass(), "invoke", Object.class);
                    return method.invoke(agent, message);
                } catch (InvocationTargetException nested) {
                    throw unwrapInvocationTarget(nested);
                } catch (ReflectiveOperationException nested) {
                    throw new IllegalArgumentException("Agent does not expose an invoke method: "
                            + agent.getClass().getName(), nested);
                }
            } catch (InvocationTargetException e) {
                throw unwrapInvocationTarget(e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to invoke agent: " + agent.getClass().getName(), e);
            }
        } catch (InvocationTargetException e) {
            throw unwrapInvocationTarget(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke agent: " + agent.getClass().getName(), e);
        }
    }

    private static Method findAccessibleMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method method = type.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static RuntimeException unwrapInvocationTarget(InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Agent invocation failed", cause);
    }
    
    /**
     * Unregister an agent.
     * 
     * @param agentId Agent ID
     */
    public AgentCard unregisterAgent(String agentId) {
        AgentCard removed = agentCards.remove(agentId);
        agentProviders.remove(agentId);
        subscriptionManager.unsubscribeAll(agentId);
        return removed;
    }

    private Supplier<?> wrapProvider(Supplier<?> provider, String agentId) {
        return () -> {
            Object agent = provider.get();
            if (agent instanceof CommunicableAgent communicableAgent) {
                communicableAgent.bindRuntime(this, agentId);
            }
            return agent;
        };
    }

    private void ensureStarted() {
        if (!running) {
            start();
        }
    }

    private static void validateSendInputs(String recipient, String sender) {
        if (sender == null || sender.isBlank()) {
            throw new IllegalArgumentException("sender is required for message tracing");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is required");
        }
    }

    private static void validatePublishInputs(String topicId, String sender) {
        if (sender == null || sender.isBlank()) {
            throw new IllegalArgumentException("sender is required for message tracing");
        }
        if (topicId == null || topicId.isBlank()) {
            throw new IllegalArgumentException("topic_id is required");
        }
    }
}
