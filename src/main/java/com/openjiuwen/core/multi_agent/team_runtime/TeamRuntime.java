/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Self-contained runtime for multi-agent communication.
 *
 * <p>Mirrors Python's {@code TeamRuntime} in
 * {@code openjiuwen/core/multi_agent/team_runtime/team_runtime.py}.</p>
 */
public class TeamRuntime implements AutoCloseable {

    private final RuntimeConfig config;
    private final String teamId;
    private final Map<String, AgentCard> agentCards = new ConcurrentHashMap<>();
    private final Map<String, Supplier<?>> agentProviders = new ConcurrentHashMap<>();
    private final Map<String, Object> activeTeamSessions = new ConcurrentHashMap<>();
    private final MessageBus messageBus;
    private volatile boolean running;
    private volatile double p2pTimeout;

    public TeamRuntime() {
        this(new RuntimeConfig());
    }

    public TeamRuntime(RuntimeConfig config) {
        this.config = config == null ? new RuntimeConfig() : config;
        this.teamId = this.config.getTeamId() == null ? "default" : this.config.getTeamId();

        MessageBusConfig busConfig = this.config.getMessageBus();
        if (busConfig == null) {
            busConfig = new MessageBusConfig();
            this.config.setMessageBus(busConfig);
        }
        busConfig.setTeamId(teamId);
        this.messageBus = new MessageBus(busConfig, this);
        this.p2pTimeout = this.config.getP2pTimeout();

        Loggers.MULTI_AGENT.info("[{}] Initialized with team_id: {}", getClass().getSimpleName(), teamId);
    }

    public boolean isRunning() {
        return running;
    }

    public double getP2pTimeout() {
        return p2pTimeout;
    }

    public void setP2pTimeout(double timeout) {
        this.p2pTimeout = timeout;
    }

    /**
     * Start the runtime.
     *
     * @return completion future
     */
    public CompletableFuture<Void> start() {
        if (running) {
            Loggers.MULTI_AGENT.warning("[{}] Already running", getClass().getSimpleName());
            return CompletableFuture.completedFuture(null);
        }
        return messageBus.start().thenRun(() -> {
            running = true;
            Loggers.MULTI_AGENT.info("[{}] Started", getClass().getSimpleName());
        });
    }

    /**
     * Stop the runtime.
     *
     * @return completion future
     */
    public CompletableFuture<Void> stop() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }
        Loggers.MULTI_AGENT.info("[{}] Stopping...", getClass().getSimpleName());
        running = false;
        return messageBus.stop().thenRun(() -> Loggers.MULTI_AGENT.info("[{}] Stopped", getClass().getSimpleName()));
    }

    @Override
    public void close() {
        stop().join();
    }

    private CompletableFuture<Void> ensureStarted() {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }
        return start();
    }

    /**
     * Register an agent by card and provider.
     *
     * @param card agent card
     * @param provider provider creating the agent instance
     */
    public void registerAgent(AgentCard card, Supplier<?> provider) {
        if (card == null || card.getId() == null || card.getId().isBlank()) {
            throw agentTeamError("agent card id is required");
        }
        if (provider == null) {
            throw agentTeamError("agent provider is required");
        }

        String agentId = card.getId();
        agentCards.put(agentId, card);
        agentProviders.put(agentId, wrapProvider(provider, agentId));
        Loggers.MULTI_AGENT.info("[{}] Registered agent: {}", getClass().getSimpleName(), agentId);
    }

    /**
     * Register an agent by a provider that accepts its card.
     *
     * @param card agent card
     * @param provider card-aware provider
     */
    public void registerAgent(AgentCard card, Function<AgentCard, ?> provider) {
        registerAgent(card, () -> provider.apply(card));
    }

    public AgentCard unregisterAgent(String agentId) {
        AgentCard removed = agentCards.remove(agentId);
        agentProviders.remove(agentId);
        if (removed != null) {
            messageBus.removeAllSubscriptions(agentId);
            Loggers.MULTI_AGENT.info("[{}] Unregistered agent: {}", getClass().getSimpleName(), agentId);
        }
        return removed;
    }

    public boolean hasAgent(String agentId) {
        return agentCards.containsKey(agentId);
    }

    public AgentCard getAgentCard(String agentId) {
        return agentCards.get(agentId);
    }

    public List<String> listAgents() {
        return new ArrayList<>(agentCards.keySet());
    }

    public int getAgentCount() {
        return agentCards.size();
    }

    public CompletableFuture<Void> cleanupSession(String sessionId) {
        return messageBus.cleanupSession(sessionId);
    }

    public void bindTeamSession(Object session) {
        String sessionId = sessionIdOf(session);
        if (sessionId != null) {
            activeTeamSessions.put(sessionId, session);
        }
    }

    public void unbindTeamSession(String sessionId) {
        if (sessionId != null) {
            activeTeamSessions.remove(sessionId);
        }
    }

    public Object getTeamSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return activeTeamSessions.get(sessionId);
    }

    public Map<String, Object> listSubscriptions(String agentId) {
        return messageBus.listSubscriptions(agentId);
    }

    public Map<String, Object> listSubscriptions() {
        return listSubscriptions(null);
    }

    public int getSubscriptionCount() {
        return messageBus.getSubscriptionCount();
    }

    public CompletableFuture<Object> send(Object message, String recipient, String sender) {
        return send(message, recipient, sender, null, null);
    }

    public CompletableFuture<Object> send(Object message, String recipient, String sender, String sessionId) {
        return send(message, recipient, sender, sessionId, null);
    }

    public CompletableFuture<Object> send(
            Object message,
            String recipient,
            String sender,
            String sessionId,
            Double timeout
    ) {
        validateSendInputs(recipient, sender);
        if (!agentCards.containsKey(recipient)) {
            throw agentTeamError("Recipient '" + recipient + "' not registered in runtime");
        }

        return ensureStarted().thenCompose(ignored -> messageBus.send(message, recipient, sender, sessionId, timeout));
    }

    public CompletableFuture<Void> publish(Object message, String topicId, String sender) {
        return publish(message, topicId, sender, null);
    }

    public CompletableFuture<Void> publish(Object message, String topicId, String sender, String sessionId) {
        validatePublishInputs(topicId, sender);
        return ensureStarted().thenCompose(ignored -> messageBus.publish(message, topicId, sender, sessionId));
    }

    public CompletableFuture<Void> subscribe(String agentId, String topic) {
        validateSubscriptionInputs(agentId, topic, "subscription");
        return messageBus.addSubscription(agentId, topic);
    }

    public CompletableFuture<Void> unsubscribe(String agentId, String topic) {
        validateSubscriptionInputs(agentId, topic, "unsubscription");
        return messageBus.removeSubscription(agentId, topic);
    }

    CompletionStage<Object> dispatchToAgent(String agentId, Object message, Object session) {
        Supplier<?> provider = agentProviders.get(agentId);
        if (provider == null) {
            return CompletableFuture.failedFuture(agentTeamError("Unknown agent: " + agentId));
        }

        Object agent;
        try {
            agent = provider.get();
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return toFuture(agent).thenCompose(resolved -> invokeAgent(agentId, resolved, message, session));
    }

    private CompletionStage<Object> invokeAgent(String agentId, Object agent, Object message, Object session) {
        if (agent == null) {
            return CompletableFuture.failedFuture(agentTeamError("Unknown agent: " + agentId));
        }
        if (agent instanceof CommunicableAgent communicableAgent) {
            communicableAgent.bindRuntime(this, agentId);
        }
        if (agent instanceof Function<?, ?> function) {
            @SuppressWarnings("unchecked")
            Function<Object, Object> typedFunction = (Function<Object, Object>) function;
            return toFuture(typedFunction.apply(message));
        }

        Method method = findInvokeMethod(agent.getClass(), true, message, session);
        Object[] args;
        if (method != null) {
            args = new Object[]{message, session};
        } else {
            method = findInvokeMethod(agent.getClass(), false, message, null);
            args = method == null ? null : new Object[]{message};
        }
        if (method == null) {
            return CompletableFuture.failedFuture(agentTeamError(
                    "Agent does not expose an invoke method: " + agent.getClass().getName()));
        }

        try {
            method.setAccessible(true);
            return toFuture(method.invoke(agent, args));
        } catch (IllegalAccessException exception) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Failed to invoke agent: " + agent.getClass().getName(), exception));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                return CompletableFuture.failedFuture(runtimeException);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Agent invocation failed", cause));
        }
    }

    private Supplier<?> wrapProvider(Supplier<?> provider, String agentId) {
        return () -> {
            Object agent = provider.get();
            if (agent instanceof CommunicableAgent communicableAgent) {
                communicableAgent.bindRuntime(this, agentId);
                Loggers.MULTI_AGENT.debug("[{}] Auto-bound runtime to CommunicableAgent '{}'",
                        getClass().getSimpleName(), agentId);
            } else {
                Loggers.MULTI_AGENT.warning("[{}] Agent '{}' does not inherit from CommunicableAgent. "
                                + "Methods send(), publish(), subscribe(), unsubscribe() will not be available on this agent.",
                        getClass().getSimpleName(), agentId);
            }
            return agent;
        };
    }

    private static Method findInvokeMethod(Class<?> type, boolean withSession, Object message, Object session) {
        int parameterCount = withSession ? 2 : 1;
        for (String name : List.of("invoke", "call", "apply")) {
            Method method = findMethod(type, name, parameterCount, message, session);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount, Object message, Object session) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterCount() == parameterCount
                        && canPass(method.getParameterTypes(), message, session)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == parameterCount
                    && canPass(method.getParameterTypes(), message, session)) {
                return method;
            }
        }
        return null;
    }

    private static boolean canPass(Class<?>[] parameterTypes, Object message, Object session) {
        if (parameterTypes.length == 0) {
            return false;
        }
        if (!canPass(parameterTypes[0], message)) {
            return false;
        }
        return parameterTypes.length < 2 || canPass(parameterTypes[1], session);
    }

    private static boolean canPass(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        if (parameterType.isPrimitive()) {
            return primitiveWrapper(parameterType).isInstance(value);
        }
        return parameterType.isInstance(value) || parameterType == Object.class;
    }

    private static Class<?> primitiveWrapper(Class<?> primitive) {
        Map<Class<?>, Class<?>> wrappers = new LinkedHashMap<>();
        wrappers.put(boolean.class, Boolean.class);
        wrappers.put(byte.class, Byte.class);
        wrappers.put(short.class, Short.class);
        wrappers.put(int.class, Integer.class);
        wrappers.put(long.class, Long.class);
        wrappers.put(float.class, Float.class);
        wrappers.put(double.class, Double.class);
        wrappers.put(char.class, Character.class);
        return wrappers.getOrDefault(primitive, primitive);
    }

    private static CompletionStage<Object> toFuture(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage.thenApply(item -> item);
        }
        return CompletableFuture.completedFuture(value);
    }

    private static String sessionIdOf(Object session) {
        if (session == null) {
            return null;
        }
        if (session instanceof AgentSessionApi api) {
            return api.getSessionId();
        }
        for (String methodName : List.of("getSessionId", "get_session_id")) {
            try {
                Method method = session.getClass().getMethod(methodName);
                Object value = method.invoke(session);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {
                // Try the next method name.
            }
        }
        return null;
    }

    private static void validateSendInputs(String recipient, String sender) {
        if (sender == null || sender.isBlank()) {
            throw agentTeamError("sender is required for message tracing");
        }
        if (recipient == null || recipient.isBlank()) {
            throw agentTeamError("recipient is required");
        }
    }

    private static void validatePublishInputs(String topicId, String sender) {
        if (sender == null || sender.isBlank()) {
            throw agentTeamError("sender is required for message tracing");
        }
        if (topicId == null || topicId.isBlank()) {
            throw agentTeamError("topic_id is required");
        }
    }

    private static void validateSubscriptionInputs(String agentId, String topic, String operation) {
        if (agentId == null || agentId.isBlank()) {
            throw agentTeamError("agent_id is required for " + operation);
        }
        if (topic == null || topic.isBlank()) {
            throw agentTeamError("topic is required for " + operation);
        }
    }

    private static RuntimeException agentTeamError(String message) {
        return ErrorHelper.buildError(StatusCode.AGENT_TEAM_EXECUTION_ERROR, "error_msg", message);
    }

    public RuntimeConfig getConfig() {
        return config;
    }

    public String getTeamId() {
        return teamId;
    }

    public MessageBus getMessageBus() {
        return messageBus;
    }
}
