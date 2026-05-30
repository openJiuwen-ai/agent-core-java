/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client with session management and connection pooling.
 * <p>
 * Mirrors Python's {@code HttpClient} from
 * {@code core/common/clients/http_client.py}.
 */
public class HttpClient extends BaseClient {

    private SessionConfig config;
    private java.net.http.HttpClient underlyingClient;
    private boolean closed;

    public HttpClient() {
        this(new SessionConfig());
    }

    public HttpClient(SessionConfig config) {
        this.config = config;
        this.underlyingClient = buildClient(config);
    }

    public static String getClientName() {
        return "http";
    }

    public static String getClientType() {
        return "common";
    }

    private java.net.http.HttpClient buildClient(SessionConfig config) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();

        if (config.getConnectTimeout() != null) {
            builder.connectTimeout(seconds(config.getConnectTimeout()));
        }

        if (config.getProxy() != null && !config.getProxy().isBlank()) {
            builder.proxy(proxySelector(config.getProxy()));
        }

        return builder.build();
    }

    public SessionConfig getConfig() {
        return config;
    }

    public HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(url)
                .GET()
                .build();
        return underlyingClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return underlyingClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> getAsync(String url) {
        HttpRequest request = requestBuilder(url)
                .GET()
                .build();
        return underlyingClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> postAsync(String url, String body) {
        HttpRequest request = requestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return underlyingClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void initialize(Map<String, Object> config) {
        this.config = normalizeConfig(config);
        this.underlyingClient = buildClient(this.config);
        this.closed = false;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isHealthy() {
        return !closed;
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (config.getTimeout() != null) {
            builder.timeout(seconds(config.getTimeout()));
        }
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(builder::header);
        }
        return builder;
    }

    private static SessionConfig normalizeConfig(Map<String, Object> rawConfig) {
        SessionConfig sessionConfig = new SessionConfig();
        if (rawConfig == null || rawConfig.isEmpty()) {
            return sessionConfig;
        }

        sessionConfig.setTimeout(asDouble(first(rawConfig, "timeout")));
        sessionConfig.setConnectTimeout(asDouble(first(rawConfig, "connect_timeout", "connectTimeout")));
        sessionConfig.setProxy(asString(first(rawConfig, "proxy")));
        sessionConfig.setRaiseForStatus(asBoolean(first(rawConfig, "raise_for_status", "raiseForStatus"), false));
        sessionConfig.setTrustEnv(asBoolean(first(rawConfig, "trust_env", "trustEnv"), true));
        sessionConfig.setHeaders(stringMap(first(rawConfig, "headers")));
        sessionConfig.setTimeoutArgs(stringMap(first(rawConfig, "timeout_args", "timeoutArgs")));
        sessionConfig.setExtendArgs(objectMap(first(rawConfig, "extend_args", "extendArgs")));
        Object auth = first(rawConfig, "auth");
        if (auth != null) {
            sessionConfig.setAuth(auth);
        }
        return sessionConfig;
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            return Double.parseDouble(String.valueOf(value));
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new HashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new HashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return result;
    }

    private static Duration seconds(Double value) {
        return Duration.ofMillis(Math.max(0L, Math.round(value * 1000.0)));
    }

    private static ProxySelector proxySelector(String proxy) {
        URI uri = proxy.contains("://") ? URI.create(proxy) : URI.create("http://" + proxy);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
    }
}
