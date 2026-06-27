/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClient;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientFactory;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.schema.AgentResult;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A2A remote client plugin implementation.
 *
 * <p>Mirrors Python's {@code A2ARemoteClient} in
 * {@code openjiuwen/extensions/a2a/a2a_remote_client.py}.</p>
 */
public class A2ARemoteClient implements RemoteClient {
    private static final LoggerProtocol LOGGER = Loggers.RUNNER;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> RESULT_MAP_TYPE = new TypeReference<>() {
    };

    private final RemoteClientConfig config;
    private final A2AClient client;
    private volatile boolean started;

    static {
        RemoteClientFactory.registerRemoteClient(ProtocolEnum.A2A.name(), A2ARemoteClient::new);
    }

    public A2ARemoteClient(RemoteClientConfig config) {
        this.config = config == null ? RemoteClientConfig.builder().build() : config;
        try {
            Object card = resolveA2aCard();
            if (card == null) {
                throw new IllegalArgumentException("card is required when protocol is A2A");
            }
            boolean polling = asBoolean(kwargs().get("polling"));
            this.client = createClient(card, polling);
            LOGGER.info("[A2ARemoteClient] Initialized client for {}, url={}",
                    this.config.getId(), this.config.getUrl());
        } catch (RuntimeException exception) {
            LOGGER.error("[A2ARemoteClient] Failed to initialize client for {}: {}",
                    this.config.getId(), exception.getMessage());
            throw exception;
        }
    }

    @Override
    public CompletionStage<Void> start() {
        started = true;
        LOGGER.debug("[A2ARemoteClient] Started client for {}", config.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> stop() {
        if (!started) {
            return CompletableFuture.completedFuture(null);
        }
        return client.stop().thenRun(() -> {
            started = false;
            LOGGER.debug("[A2ARemoteClient] Stopped client for {}", config.getId());
        });
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
        LOGGER.debug("[A2ARemoteClient] Invoke {}", config.getId());
        Map<String, Object> safeInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
        String sessionId = A2AClient.resolveSessionId(safeInputs);
        try {
            CompletionStage<AgentResult> invocation = client.invoke(safeInputs);
            CompletionStage<AgentResult> timed = withTimeout(invocation, timeoutSeconds, "A2A invoke timeout");
            return timed.handle((result, error) -> {
                if (error != null) {
                    stopAfterFailure("Invoke", error);
                    throw propagate(error);
                }
                return agentResultToMap(A2AClient.withSessionId(result, sessionId));
            });
        } catch (Throwable error) {
            stopAfterFailure("Invoke", error);
            throw propagate(error);
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
        LOGGER.debug("[A2ARemoteClient] Stream {}", config.getId());
        Map<String, Object> safeInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
        String sessionId = A2AClient.resolveSessionId(safeInputs);
        long startedAt = System.nanoTime();
        Iterator<AgentResult> delegate;
        try {
            delegate = client.stream(safeInputs).iterator();
        } catch (Throwable error) {
            stopAfterFailure("Stream", error);
            throw propagate(error);
        }

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                checkTimeout();
                try {
                    boolean hasNext = delegate.hasNext();
                    checkTimeout();
                    return hasNext;
                } catch (Throwable error) {
                    stopAfterFailure("Stream", error);
                    throw propagate(error);
                }
            }

            @Override
            public Object next() {
                checkTimeout();
                try {
                    AgentResult result = delegate.next();
                    checkTimeout();
                    return agentResultToMap(A2AClient.withSessionId(result, sessionId));
                } catch (Throwable error) {
                    stopAfterFailure("Stream", error);
                    throw propagate(error);
                }
            }

            private void checkTimeout() {
                if (!hasTimeout(timeoutSeconds)) {
                    return;
                }
                double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0d;
                if (elapsedSeconds > timeoutSeconds) {
                    TimeoutException timeout = new TimeoutException("A2A stream timeout");
                    stopAfterFailure("Stream", timeout);
                    throw new CompletionException(timeout);
                }
            }
        };
    }

    public CompletionStage<AgentResult> cancelTask(String taskId) {
        return cancelTask(taskId, null);
    }

    public CompletionStage<AgentResult> cancelTask(String taskId, String tenant) {
        LOGGER.debug("[A2ARemoteClient] Cancel task {} for {}", taskId, config.getId());
        try {
            return client.cancelTask(taskId, tenant).handle((result, error) -> {
                if (error != null) {
                    stopAfterFailure("Cancel task", error);
                    throw propagate(error);
                }
                return result;
            });
        } catch (Throwable error) {
            stopAfterFailure("Cancel task", error);
            throw propagate(error);
        }
    }

    public CompletionStage<AgentResult> cancel_task(String taskId) {
        return cancelTask(taskId);
    }

    public CompletionStage<AgentResult> cancel_task(String taskId, String tenant) {
        return cancelTask(taskId, tenant);
    }

    A2AClient getClient() {
        return client;
    }

    protected A2AClient createClient(Object card, boolean polling) {
        Object factoryCandidate = kwargs().get("clientFactory");
        if (!(factoryCandidate instanceof A2AClient.A2AClientFactory)) {
            factoryCandidate = kwargs().get("client_factory");
        }
        if (factoryCandidate instanceof A2AClient.A2AClientFactory factory) {
            return new A2AClient(card, polling, factory);
        }
        return new A2AClient(card, polling);
    }

    private Object resolveA2aCard() {
        Object candidate = kwargs().get("card");
        if (candidate == null) {
            return null;
        }
        if (!(candidate instanceof AgentCard card)) {
            throw new IllegalArgumentException("card in config.kwargs must be an openjiuwen AgentCard");
        }
        return convertToA2aCard(card);
    }

    private A2AAgentCardAdapter.A2aAgentCard convertToA2aCard(AgentCard card) {
        String interfaceUrl = null;
        String url = config.getUrl();
        if (url != null && !url.isBlank()) {
            String normalized = url.stripTrailing();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            interfaceUrl = normalized.endsWith("/a2a/jsonrpc")
                    ? normalized + "/"
                    : normalized + "/a2a/jsonrpc/";
        }
        return A2AAgentCardAdapter.toA2aAgentCard(card, interfaceUrl, "JSONRPC", "1.0", null, null);
    }

    private Map<String, Object> kwargs() {
        return config.getKwargs() == null ? Map.of() : config.getKwargs();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static CompletionStage<AgentResult> withTimeout(CompletionStage<AgentResult> source,
                                                           Double timeoutSeconds,
                                                           String message) {
        if (!hasTimeout(timeoutSeconds)) {
            return source;
        }
        CompletableFuture<AgentResult> timeout = new CompletableFuture<>();
        long timeoutMillis = Math.max(0L, (long) Math.ceil(timeoutSeconds * 1000.0d));
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS)
                .execute(() -> timeout.completeExceptionally(new TimeoutException(message)));
        return source.toCompletableFuture().applyToEither(timeout, Function.identity());
    }

    private static boolean hasTimeout(Double timeoutSeconds) {
        return timeoutSeconds != null && Double.compare(timeoutSeconds, 0.0d) != 0;
    }

    private static Map<String, Object> agentResultToMap(AgentResult result) {
        AgentResult safeResult = result == null ? new AgentResult() : result;
        return MAPPER.convertValue(safeResult, RESULT_MAP_TYPE);
    }

    private void stopAfterFailure(String operation, Throwable error) {
        LOGGER.error("[A2ARemoteClient] {} failed for {}: {}", operation, config.getId(), unwrap(error).getMessage());
        try {
            stop().toCompletableFuture().join();
        } catch (RuntimeException ignored) {
            // Python logs and re-raises the original operation failure.
        }
    }

    private static RuntimeException propagate(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new CompletionException(cause);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.lang.reflect.InvocationTargetException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
