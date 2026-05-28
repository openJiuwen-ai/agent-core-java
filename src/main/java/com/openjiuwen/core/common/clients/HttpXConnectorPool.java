/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * HTTPX-style connector pool implementation.
 * <p>
 * Mirrors Python's {@code HttpXConnectorPool} class from
 * <code>openjiuwen/core/common/clients/llm_client.py</code>.
 *
 * <p>Provides a connection pool using Java's HttpClient, enabling efficient
 * connection reuse for HTTP/1.1 and HTTP/2 requests.
 */
public class HttpXConnectorPool extends RefCountedResource {

    private HttpClient httpClient;
    private HttpXConnectorPoolConfig config;

    public HttpXConnectorPool(HttpXConnectorPoolConfig config) {
        super();
        this.config = config;
        initHttpClient(config);
    }

    private void initHttpClient(HttpXConnectorPoolConfig config) {
        int timeout = 30; // default timeout
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (config.getProxy() != null) {
            // Java HttpClient proxy configuration would be set here
            // For now, we use default settings
        }

        // Configure connection pool settings
        // Java's HttpClient has built-in connection pooling
        builder.version(HttpClient.Version.HTTP_2);

        this.httpClient = builder.build();
    }

    /**
     * Get the underlying HttpClient instance.
     *
     * @return the HttpClient instance
     */
    public Object getConn() {
        return httpClient;
    }
    
    /**
     * Alias for compatibility.
     */
    public Object conn() {
        return getConn();
    }

    /**
     * Perform the actual close operation.
     * Java HttpClient doesn't require explicit closing.
     */
    @Override
    protected CompletableFuture<Void> doClose() {
        // HttpClient handles cleanup through garbage collection
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get the HttpClient for async operations.
     *
     * @return CompletableFuture with HttpClient
     */
    public CompletableFuture<HttpClient> getAsyncClient() {
        return CompletableFuture.completedFuture(httpClient);
    }
}