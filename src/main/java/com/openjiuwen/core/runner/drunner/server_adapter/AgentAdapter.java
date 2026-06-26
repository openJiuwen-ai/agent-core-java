/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Exposes a local agent through MQ and optional A2A server adapters.
 *
 * <p>Mirrors Python's {@code AgentAdapter} in
 * {@code openjiuwen/core/runner/drunner/server_adapter/agent_adapter.py}.</p>
 */
public class AgentAdapter {

    private static final MqServerAdapterFactory DEFAULT_MQ_SERVER_ADAPTER_FACTORY = MqServerAdapter::new;
    private static volatile MqServerAdapterFactory mqServerAdapterFactory = DEFAULT_MQ_SERVER_ADAPTER_FACTORY;

    private final String agentId;
    private final String version;
    private final AgentCard agentCard;
    private final String interfaceUrl;
    private final boolean enableA2a;
    private final String topic;
    private final MqServerAdapter server;
    private final Object a2aServer;

    public AgentAdapter(String agentId) {
        this(agentId, "", null);
    }

    public AgentAdapter(String agentId, String version, AgentCard agentCard) {
        this.agentId = agentId;
        this.version = version == null ? "" : version;
        this.agentCard = agentCard;
        this.interfaceUrl = agentCard == null ? null : agentCard.getInterfaceUrl();
        this.enableA2a = readEnableA2a();
        if (enableA2a && agentCard == null) {
            throw new IllegalArgumentException("agent_card is required when enable_a2a is True");
        }
        this.topic = agentTopic(agentId, this.version);
        this.server = mqServerAdapterFactory.create(
                agentId,
                topic,
                this::handleInvoke,
                this::handleStream);
        this.a2aServer = enableA2a ? createA2aServer() : null;
        if (enableA2a && a2aServer == null) {
            throw new IllegalStateException("failed to create server adapter for A2A");
        }
    }

    public void start() {
        server.start();
        if (a2aServer != null) {
            invokeNoArg(a2aServer, "start");
        }
    }

    public CompletionStage<Void> stop() {
        CompletionStage<Void> mqStop = server.stop();
        if (a2aServer == null) {
            return mqStop;
        }
        return mqStop.thenCompose(ignored -> toVoidStage(invokeNoArg(a2aServer, "stop")));
    }

    public String getAgentId() {
        return agentId;
    }

    public String getVersion() {
        return version;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public String getInterfaceUrl() {
        return interfaceUrl;
    }

    public boolean isEnableA2a() {
        return enableA2a;
    }

    public String getTopic() {
        return topic;
    }

    public MqServerAdapter getServer() {
        return server;
    }

    public Object getA2aServer() {
        return a2aServer;
    }

    static void setMqServerAdapterFactoryForTest(MqServerAdapterFactory factory) {
        mqServerAdapterFactory = factory == null ? DEFAULT_MQ_SERVER_ADAPTER_FACTORY : factory;
    }

    static void resetMqServerAdapterFactoryForTest() {
        mqServerAdapterFactory = DEFAULT_MQ_SERVER_ADAPTER_FACTORY;
    }

    private Object handleInvoke(Map<String, Object> inputs) {
        return MqServerAdapter.RunnerAccess.runAgent(agentId, inputs);
    }

    private Object handleStream(Map<String, Object> inputs) {
        return MqServerAdapter.RunnerAccess.runAgentStreaming(agentId, inputs);
    }

    private Object createA2aServer() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("adapter_id", agentId);
        kwargs.put("version", version);
        kwargs.put("agent_card", agentCard);
        kwargs.put("invoke_handler", (java.util.function.Function<Map<String, Object>, Object>) this::handleInvoke);
        kwargs.put("stream_handler", (java.util.function.Function<Map<String, Object>, Object>) this::handleStream);
        return ServerAdapterRegistry.createServerAdapter("A2A", kwargs);
    }

    private static boolean readEnableA2a() {
        Object config = runnerConfig();
        Object value = readProperty(config, "isEnableA2a", "enableA2a");
        if (value == null) {
            value = readProperty(config, "getEnableA2a", "enable_a2a");
        }
        return value instanceof Boolean bool && bool;
    }

    private static String agentTopic(String agentId, String version) {
        Object config = runnerConfig();
        String template = invokeString(config, "agentTopicTemplate");
        if (template == null || template.isBlank()) {
            Object distributedConfig = readProperty(config, "getDistributedConfig", "distributedConfig");
            template = invokeString(distributedConfig, "getAgentTopicTemplate", "");
        }
        if (template == null || template.isBlank()) {
            template = "openjiuwen.single_agent.{agent_id}.{version}";
        }
        return template.replace("{agent_id}", agentId).replace("{version}", version == null ? "" : version);
    }

    private static Object runnerConfig() {
        try {
            Class<?> runnerConfigClass = Class.forName("com.openjiuwen.core.runner.RunnerConfig");
            Method method = runnerConfigClass.getMethod("getRunnerConfig");
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readProperty(Object target, String getterName, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                try {
                    Object result = method.invoke(target, args);
                    return result == null ? null : String.valueOf(result);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to call " + methodName + " on A2A server", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static CompletionStage<Void> toVoidStage(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return ((CompletionStage<Object>) stage).thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    @FunctionalInterface
    interface MqServerAdapterFactory {
        MqServerAdapter create(String adapterId,
                               String topic,
                               Function<Map<String, Object>, Object> invokeHandler,
                               Function<Map<String, Object>, Object> streamHandler);
    }
}
