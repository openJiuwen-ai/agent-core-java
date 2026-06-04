/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.runner.drunner.remote_client.RemoteClient;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * A2A remote client for communicating with external agents.
 * <p>
 * Mirrors Python's {@code A2ARemoteClient} in
 * {@code openjiuwen.extensions.a2a.a2a_remote_client}.
 */
public class A2ARemoteClient implements RemoteClient {

    private final String endpoint;
    private final Map<String, Object> card;
    private final A2AClient.MessageTransport transport;
    private final A2AClient client;
    private boolean connected;
    private boolean started;

    public A2ARemoteClient(String endpoint) {
        this(endpoint, Map.of());
    }

    public A2ARemoteClient(String endpoint, Map<String, Object> card) {
        this.endpoint = endpoint;
        this.card = card != null ? new LinkedHashMap<>(card) : new LinkedHashMap<>();
        this.transport = null;
        this.client = createClient(this.card);
    }

    public A2ARemoteClient(RemoteClientConfig config, AgentCard card) {
        this(config, resolveCard(config, card), true);
    }

    public A2ARemoteClient(RemoteClientConfig config) {
        this(config, resolveCard(config, null), true);
    }

    private A2ARemoteClient(RemoteClientConfig config, AgentCard card, boolean ignored) {
        if (card == null) {
            throw new IllegalArgumentException("card is not openjiuwen agent card");
        }
        this.endpoint = config != null ? config.getUrl() : null;
        this.transport = resolveTransport(config);
        A2AAgentCardAdapter.A2aAgentCard a2aCard = convertToA2aCard(card, endpoint);
        if (a2aCard == null) {
            throw new IllegalArgumentException("failed to convert openjiuwen agent card to a2a agent card");
        }
        this.card = a2aCard.toMap();
        this.client = createClient(this.card);
    }

    protected A2AClient createClient(Map<String, Object> a2aCard) {
        return new A2AClient(a2aCard, transport);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Map<String, Object> getCard() {
        return Collections.unmodifiableMap(card);
    }

    public boolean connectToRemote() {
        connected = endpoint != null && !endpoint.isBlank();
        return connected;
    }

    public void disconnectFromRemote() {
        connected = false;
        stop();
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isClosed() {
        return client.isClosed();
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void stop() {
        if (started || !client.isClosed()) {
            client.stop();
        }
        connected = false;
        started = false;
    }

    public Map<String, Object> invoke(Map<String, Object> inputs) throws Exception {
        return invoke(inputs, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            return client.invoke(inputs);
        } catch (RuntimeException e) {
            stop();
            throw e;
        }
    }

    public Iterator<Object> stream(Map<String, Object> inputs) throws Exception {
        return stream(inputs, null);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        Iterator<Map<String, Object>> delegate = client.stream(inputs).iterator();
        long startedAt = System.nanoTime();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                checkTimeout();
                boolean result = delegate.hasNext();
                checkTimeout();
                return result;
            }

            @Override
            public Object next() {
                checkTimeout();
                Object result = delegate.next();
                checkTimeout();
                return result;
            }

            private void checkTimeout() {
                if (timeoutSeconds == null) {
                    return;
                }
                double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                if (elapsedSeconds > timeoutSeconds) {
                    stop();
                    throw new RuntimeException(new TimeoutException("A2A stream timeout"));
                }
            }
        };
    }

    private static AgentCard resolveCard(RemoteClientConfig config, AgentCard explicitCard) {
        if (explicitCard != null) {
            return explicitCard;
        }
        Object candidate = config != null && config.getKwargs() != null ? config.getKwargs().get("card") : null;
        return candidate instanceof AgentCard agentCard ? agentCard : null;
    }

    private static A2AClient.MessageTransport resolveTransport(RemoteClientConfig config) {
        Object candidate = config != null && config.getKwargs() != null ? config.getKwargs().get("transport") : null;
        return candidate instanceof A2AClient.MessageTransport messageTransport ? messageTransport : null;
    }

    private static A2AAgentCardAdapter.A2aAgentCard convertToA2aCard(AgentCard card, String endpoint) {
        String interfaceUrl = null;
        if (endpoint != null && !endpoint.isBlank()) {
            String normalized = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            interfaceUrl = normalized.endsWith("/a2a/jsonrpc")
                    ? normalized + "/"
                    : normalized + "/a2a/jsonrpc/";
        }
        return A2AAgentCardAdapter.toA2aAgentCard(
                card,
                List.of(),
                interfaceUrl,
                "JSONRPC",
                "1.0",
                null);
    }
}
