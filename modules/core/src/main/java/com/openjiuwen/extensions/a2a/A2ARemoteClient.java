/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClient;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientConfig;

import java.net.http.HttpClient;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A2A-backed remote client implementation using the JSON-RPC transport.
 * <p>
 * Implements the {@link RemoteClient} interface to communicate with remote agents
 * via the Agent-to-Agent (A2A) protocol. Supports both synchronous invocation
 * and streaming response modes.
 * 
 * @since 0.1.7
 */
public class A2ARemoteClient implements RemoteClient {
    private static final String KWARG_HTTP_CLIENT = "_ojw_http_client";
    private static final String KWARG_AUTH_HEADERS = "_ojw_auth_headers";

    private final RemoteClientConfig config;
    private final A2AClient client;
    private boolean isStarted;

    /**
     * Construct an A2ARemoteClient with the given configuration.
     * 
     * @param config The remote client configuration containing the A2A server URL
     * @since 0.1.7
     */
    public A2ARemoteClient(RemoteClientConfig config) {
        this.config = config;
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("A2A remote client requires a non-empty url");
        }
        HttpClient httpClient = resolveHttpClient(config);
        Map<String, String> authHeaders = resolveAuthHeaders(config);
        this.client = new A2AClient(config.getUrl(), httpClient, authHeaders);
    }

    private static HttpClient resolveHttpClient(RemoteClientConfig config) {
        if (config.getKwargs() == null) {
            return HttpClient.newHttpClient();
        }
        Object injected = config.getKwargs().get(KWARG_HTTP_CLIENT);
        if (injected instanceof HttpClient client) {
            return client;
        }
        return HttpClient.newHttpClient();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> resolveAuthHeaders(RemoteClientConfig config) {
        if (config.getKwargs() == null) {
            return Map.of();
        }
        Object injected = config.getKwargs().get(KWARG_AUTH_HEADERS);
        if (!(injected instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return headers;
    }

    /**
     * Start the remote client, marking it as active.
     * 
     * @since 0.1.7
     */
    @Override
    public void start() {
        this.isStarted = true;
    }

    /**
     * Stop the remote client, marking it as inactive.
     * 
     * @since 0.1.7
     */
    @Override
    public void stop() {
        this.isStarted = false;
    }

    /**
     * Check whether the remote client has been started.
     * 
     * @return {@code true} if the client is started, {@code false} otherwise
     * @since 0.1.7
     */
    @Override
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Invoke the A2A remote agent synchronously with the given inputs.
     * 
     * @param inputs The input map to send to the remote agent
     * @param timeoutSeconds Optional timeout in seconds for the invocation
     * @return The result of the remote invocation
     * @throws Exception if the invocation fails or the client is not started
     * @since 0.1.7
     */
    @Override
    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        ensureStarted();
        return client.invoke(inputs, timeoutSeconds);
    }

    /**
     * Stream responses from the A2A remote agent with the given inputs.
     * 
     * @param inputs The input map to send to the remote agent
     * @param timeoutSeconds Optional timeout in seconds for the streaming operation
     * @return An iterator over the streamed response objects
     * @throws Exception if the streaming operation fails or the client is not started
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        ensureStarted();
        return client.stream(inputs, timeoutSeconds);
    }

    /**
     * ensureStarted.
     * 
     * @since 0.1.7
     */
    private void ensureStarted() {
        if (!isStarted) {
            start();
        }
    }
}
