/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client with session management and connection pooling.
 * <p>
 * Mirrors Python's {@code HttpClient} from
 * {@code core/common/clients/http_client.py}.
 */
public class HttpClient extends BaseClient {

    private final SessionConfig config;
    private final java.net.http.HttpClient underlyingClient;

    public HttpClient() {
        this(new SessionConfig());
    }

    public HttpClient(SessionConfig config) {
        this.config = config;
        this.underlyingClient = buildClient(config);
    }

    private java.net.http.HttpClient buildClient(SessionConfig config) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();

        if (config.getConnectTimeout() != null) {
            builder.connectTimeout(Duration.ofMillis(config.getConnectTimeout().longValue()));
        }

        if (config.getProxy() != null) {
            builder.proxy(java.net.http.HttpClient.Builder.NO_PROXY);
        }

        return builder.build();
    }

    public SessionConfig getConfig() {
        return config;
    }

    public HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return underlyingClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return underlyingClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> getAsync(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return underlyingClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> postAsync(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return underlyingClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void initialize(Map<String, Object> config) {
        // Already initialized via constructor
    }

    @Override
    public void close() {
        // HttpClient doesn't need explicit closing
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
