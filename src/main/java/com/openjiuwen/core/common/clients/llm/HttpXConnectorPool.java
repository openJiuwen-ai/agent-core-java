/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.llm;

import com.openjiuwen.core.common.clients.ConnectorPool;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * HTTPX-based connector pool implementation.
 * <p>
 * This class provides a connection pool using HTTP client,
 * enabling efficient connection reuse for HTTP/1.1 and HTTP/2 requests.
 * <p>
 * Mirrors Python's {@code HttpXConnectorPool} from
 * {@code core/common/clients/llm_client.py}.
 */
public class HttpXConnectorPool extends ConnectorPool {

    private final HttpXConnectorPoolConfig config;
    private final java.net.http.HttpClient client;

    public HttpXConnectorPool(HttpXConnectorPoolConfig config) {
        super(config);
        this.config = config;
        this.client = buildClient(config);
    }

    private java.net.http.HttpClient buildClient(HttpXConnectorPoolConfig config) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
        
        if (config.getTimeout() != null) {
            builder.connectTimeout(Duration.ofMillis(config.getTimeout().longValue()));
        }
        
        return builder.build();
    }

    public HttpXConnectorPoolConfig getConfig() {
        return config;
    }

    public java.net.http.HttpClient getClient() {
        return client;
    }

    @Override
    public Object getConn() {
        return client;
    }

    @Override
    protected CompletableFuture<Void> doClose() {
        // HttpClient doesn't need explicit closing
        return CompletableFuture.completedFuture(null);
    }
}