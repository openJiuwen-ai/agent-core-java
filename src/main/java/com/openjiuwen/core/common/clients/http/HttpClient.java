/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
        return request("GET", url);
    }

    public HttpResponse<String> get(String url, Map<String, ?> params) throws IOException, InterruptedException {
        return request("GET", url, null, null, params);
    }

    public HttpResponse<String> post(String url, String body) throws IOException, InterruptedException {
        return request("POST", url, body, Map.of("Content-Type", "application/json"), null);
    }

    public HttpResponse<String> put(String url, String body) throws IOException, InterruptedException {
        return request("PUT", url, body, Map.of("Content-Type", "application/json"), null);
    }

    public HttpResponse<String> delete(String url) throws IOException, InterruptedException {
        return request("DELETE", url);
    }

    public HttpResponse<String> patch(String url, String body) throws IOException, InterruptedException {
        return request("PATCH", url, body, Map.of("Content-Type", "application/json"), null);
    }

    public HttpResponse<String> head(String url) throws IOException, InterruptedException {
        return request("HEAD", url);
    }

    public HttpResponse<String> options(String url) throws IOException, InterruptedException {
        return request("OPTIONS", url);
    }

    public HttpResponse<String> request(String method, String url) throws IOException, InterruptedException {
        return request(method, url, null, null, null);
    }

    public HttpResponse<String> request(String method, String url, String body,
                                        Map<String, ?> headers, Map<String, ?> params)
            throws IOException, InterruptedException {
        ensureOpen();
        HttpRequest request = requestBuilder(url, headers, params)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return underlyingClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> requestWithSizeLimit(String method, String url, long responseBytesSizeLimit)
            throws IOException, InterruptedException {
        ensureOpen();
        HttpRequest request = requestBuilder(url, null, null)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<byte[]> response = underlyingClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.body().length > responseBytesSizeLimit) {
            throw new IllegalArgumentException(
                    "Response too large: " + response.body().length + " > " + responseBytesSizeLimit);
        }
        return new StringHttpResponse(response, new String(response.body(), StandardCharsets.UTF_8));
    }

    public List<byte[]> streamGet(String url, int chunkSize) throws IOException, InterruptedException {
        return streamGet(url, chunkSize, null);
    }

    public List<byte[]> streamGet(String url, int chunkSize, Function<byte[], byte[]> onStreamReceived)
            throws IOException, InterruptedException {
        ensureOpen();
        HttpRequest request = requestBuilder(url, null, null).GET().build();
        byte[] body = underlyingClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        List<byte[]> chunks = new ArrayList<>();
        int safeChunkSize = Math.max(1, chunkSize);
        for (int offset = 0; offset < body.length; offset += safeChunkSize) {
            int end = Math.min(body.length, offset + safeChunkSize);
            byte[] chunk = java.util.Arrays.copyOfRange(body, offset, end);
            chunks.add(onStreamReceived != null ? onStreamReceived.apply(chunk) : chunk);
        }
        return chunks;
    }

    public CompletableFuture<HttpResponse<String>> getAsync(String url) {
        ensureOpen();
        HttpRequest request = requestBuilder(url, null, null).GET().build();
        return underlyingClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> postAsync(String url, String body) {
        ensureOpen();
        HttpRequest request = requestBuilder(url, Map.of("Content-Type", "application/json"), null)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
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

    public boolean isClosed() {
        return closed;
    }

    public Map<String, String> buildRequestHeaders(Map<String, ?> headers) {
        Map<String, String> merged = new java.util.LinkedHashMap<>();
        if (config.getHeaders() != null) {
            merged.putAll(config.getHeaders());
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.put(key, String.valueOf(value));
                }
            });
        }
        return merged;
    }

    private HttpRequest.Builder requestBuilder(String url, Map<String, ?> headers, Map<String, ?> params) {
        URI uri = URI.create(appendParams(url, params));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
        if (config.getTimeout() != null) {
            builder.timeout(seconds(config.getTimeout()));
        }
        buildRequestHeaders(headers).forEach(builder::setHeader);
        return builder;
    }

    private void ensureOpen() {
        if (closed) {
            throw new RuntimeException("HttpClient is closed");
        }
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

    private static String appendParams(String url, Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            first = false;
        }
        return builder.toString();
    }

    private static ProxySelector proxySelector(String proxy) {
        URI uri = proxy.contains("://") ? URI.create(proxy) : URI.create("http://" + proxy);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
    }

    private record StringHttpResponse(HttpResponse<byte[]> delegate, String body) implements HttpResponse<String> {
        @Override
        public int statusCode() {
            return delegate.statusCode();
        }

        @Override
        public HttpRequest request() {
            return delegate.request();
        }

        @Override
        public java.util.Optional<HttpResponse<String>> previousResponse() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return delegate.headers();
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return delegate.sslSession();
        }

        @Override
        public URI uri() {
            return delegate.uri();
        }

        @Override
        public java.net.http.HttpClient.Version version() {
            return delegate.version();
        }
    }
}
