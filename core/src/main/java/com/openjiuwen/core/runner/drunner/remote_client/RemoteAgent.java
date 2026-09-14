/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

/**
 * Remote-agent facade that delegates invocations to a configured remote client.
 *
 * <p>Mirrors Python's {@code RemoteAgent} in
 * {@code openjiuwen/core/runner/drunner/remote_client/remote_agent.py}.</p>
 */
public class RemoteAgent {

    private final String agentId;
    private final String version;
    private final String description;
    private final String topic;
    private final ProtocolEnum protocol;
    private final RemoteClient client;

    public RemoteAgent(String agentId) {
        this(agentId, "", null, null, ProtocolEnum.MQ, null);
    }

    public RemoteAgent(String agentId, String version, String description, String topic,
                       ProtocolEnum protocol, Map<String, Object> config) {
        this.agentId = agentId;
        this.version = version == null ? "" : version;
        this.description = description;
        this.topic = topic == null ? RunnerConfigAccess.agentTopic(agentId, this.version) : topic;
        this.protocol = protocol == null ? ProtocolEnum.MQ : protocol;

        Map<String, Object> rawConfig = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
        Map<String, Object> kwargs = asStringKeyMap(rawConfig.getOrDefault("kwargs", rawConfig));
        RemoteClientConfig clientConfig = RemoteClientConfig.builder()
                .id(agentId)
                .version(this.version)
                .description(description)
                .protocol(this.protocol)
                .topic(this.topic)
                .url(stringOrNull(rawConfig.get("url")))
                .kwargs(kwargs)
                .build();
        this.client = RemoteClientFactory.createRemoteClient(this.protocol, clientConfig);
        if (this.client == null) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", this.agentId,
                    "reason", "failed to create remote client for protocol " + this.protocol);
        }
    }

    public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs) {
        return invoke(inputs, null);
    }

    public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
        try {
            CompletionStage<Void> startStage = client.isStarted()
                    ? CompletableFuture.completedFuture(null)
                    : client.start();
            return startStage.thenCompose(ignored -> client.invoke(inputs, timeoutSeconds))
                    .exceptionally(error -> {
                        throw translateFailure(error, timeoutSeconds);
                    });
        } catch (Throwable error) {
            throw translateFailure(error, timeoutSeconds);
        }
    }

    public Iterator<Object> stream(Map<String, Object> inputs) {
        return stream(inputs, null);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
        try {
            if (!client.isStarted()) {
                client.start().toCompletableFuture().join();
            }
            return client.stream(inputs, timeoutSeconds);
        } catch (Throwable error) {
            throw translateFailure(error, timeoutSeconds);
        }
    }

    public CompletionStage<Object> cancelTask(String taskId) {
        return cancelTask(taskId, null);
    }

    public CompletionStage<Object> cancelTask(String taskId, String tenant) {
        if (protocol != ProtocolEnum.A2A) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", agentId,
                    "reason", "cancel_task is only supported for A2A remote agents");
        }

        try {
            if (!client.isStarted()) {
                client.start().toCompletableFuture().join();
            }
            Method method = client.getClass().getMethod("cancelTask", String.class, String.class);
            Object result = method.invoke(client, taskId, tenant);
            if (result instanceof CompletionStage<?> stage) {
                return stage.thenApply(value -> value);
            }
            return CompletableFuture.completedFuture(result);
        } catch (Throwable error) {
            throw translateFailure(error, null);
        }
    }

    public String getAgentId() {
        return agentId;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getTopic() {
        return topic;
    }

    public ProtocolEnum getProtocol() {
        return protocol;
    }

    RemoteClient getClient() {
        return client;
    }

    private BaseError translateFailure(Throwable error, Double timeoutSeconds) {
        Throwable cause = unwrap(error);
        if (cause instanceof BaseError baseError) {
            return baseError;
        }
        if (cause instanceof TimeoutException) {
            return ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT,
                    null,
                    null,
                    cause,
                    Map.of("agent_id", agentId, "timeout", String.valueOf(timeoutSeconds)));
        }
        if (cause instanceof CancellationException || cause instanceof InterruptedException) {
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    null,
                    null,
                    cause,
                    Map.of("agent_id", agentId, "reason", "cancelled"));
        }
        return ErrorHelper.buildError(
                StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                null,
                null,
                cause,
                Map.of("agent_id", agentId, "reason", cause.getMessage() == null ? "failed" : cause.getMessage()));
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.lang.reflect.InvocationTargetException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
