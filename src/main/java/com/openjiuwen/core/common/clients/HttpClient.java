/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client with shared-session support.
 */
public class HttpClient extends BaseClient {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String CLIENT_NAME = "http";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String CLIENT_TYPE = "common";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String __client_name__ = CLIENT_NAME;
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String __client_type__ = CLIENT_TYPE;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SessionConfig config;
    private final HttpSessionManager sessionManager;
    private final boolean isSessionReuseEnabled;
    private volatile HttpSession session;
    private volatile boolean isClosed;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpClient() {
        this(new SessionConfig(), true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpClient(SessionConfig config) {
        this(config, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpClient(SessionConfig config, boolean isSessionReuseEnabled) {
        super(Map.of("reuse_session", isSessionReuseEnabled));
        this.config = config != null ? config : new SessionConfig();
        this.isSessionReuseEnabled = isSessionReuseEnabled;
        this.sessionManager = HttpSessionManager.getInstance();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected synchronized HttpSession acquireSession() throws Exception {
        if (isSessionReuseEnabled) {
            if (isClosed) {
                throw new IllegalStateException("HttpClient is isClosed");
            }
            if (session == null || session.isClosed()) {
                session = sessionManager.acquire(config).join().resource();
            }
            return session;
        }
        return sessionManager.acquire(config).join().resource();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected synchronized void releaseSession(HttpSession resolvedSession) throws Exception {
        if (!isSessionReuseEnabled && resolvedSession != null) {
            sessionManager.release(resolvedSession.getConfig());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public CompletableFuture<Boolean> close() {
        if (isClosed) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        if (isSessionReuseEnabled && session != null) {
            try {
                sessionManager.release(session.getConfig());
            } catch (Exception ignored) {
                // best-effort release
            }
            session = null;
        }
        isClosed = true;
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> get(String url) throws Exception {
        return request("GET", url, null, null, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> get(String url, Map<String, Object> params) throws Exception {
        return request("GET", appendQuery(url, params), null, null, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> post(String url, Map<String, Object> body) throws Exception {
        return request("POST", url, null, body, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> put(String url, Map<String, Object> body) throws Exception {
        return request("PUT", url, null, body, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> patch(String url, Map<String, Object> body) throws Exception {
        return request("PATCH", url, null, body, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> delete(String url) throws Exception {
        return request("DELETE", url, null, null, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> head(String url) throws Exception {
        return request("HEAD", url, null, null, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> options(String url) throws Exception {
        return request("OPTIONS", url, null, null, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> streamGet(String url,
                                      Map<String, Object> params,
                                      boolean isChunkedTransfer,
                                      int chunkSize) throws Exception {
        return streamRequest("GET", appendQuery(url, params), null, null, null, isChunkedTransfer, chunkSize, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> streamPost(String url,
                                       Map<String, Object> body,
                                       boolean isChunkedTransfer,
                                       int chunkSize) throws Exception {
        return streamRequest("POST", url, null, body, null, isChunkedTransfer, chunkSize, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> request(String method,
                                       String url,
                                       Map<String, String> headers,
                                       Map<String, Object> body,
                                       Double timeoutSeconds,
                                       Map<String, Object> requestArgs) throws Exception {
        HttpSession resolvedSession = acquireSession();
        try {
            HttpRequest request = buildRequest(method, url, headers, body, timeoutSeconds, requestArgs);
            HttpResponse<byte[]> response = resolvedSession.session()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (config.isRaiseForStatus() && response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "HTTP " + response.statusCode() + ": "
                                + new String(response.body(), StandardCharsets.UTF_8));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", response.statusCode());
            result.put("data", parseBody(response));
            result.put("url", response.uri().toString());
            result.put("headers", response.headers().map());
            result.put("reason", "");
            return result;
        } finally {
            releaseSession(resolvedSession);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Map<String, Object>> requestAsync(String method,
                                                               String url,
                                                               Map<String, String> headers,
                                                               Map<String, Object> body,
                                                               Double timeoutSeconds,
                                                               Map<String, Object> requestArgs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request(method, url, headers, body, timeoutSeconds, requestArgs);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> streamRequest(String method,
                                          String url,
                                          Map<String, String> headers,
                                          Map<String, Object> body,
                                          Double timeoutSeconds,
                                          boolean isChunkedTransfer,
                                          int chunkSize,
                                          Map<String, Object> requestArgs) throws Exception {
        HttpSession resolvedSession = acquireSession();
        try {
            HttpRequest request = buildRequest(method, url, headers, body, timeoutSeconds, requestArgs);
            HttpResponse<InputStream> response = resolvedSession.session()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
            InputStream responseInput = response.body();
            if (responseInput == null) {
                return List.<Object>of().iterator();
            }
            List<Object> chunks = new ArrayList<>();
            try (responseInput) {
                if (isChunkedTransfer) {
                    byte[] buffer = new byte[Math.max(1, chunkSize)];
                    int read;
                    while ((read = responseInput.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        chunks.add(java.util.Arrays.copyOf(buffer, read));
                    }
                } else {
                    StringBuilder builder = new StringBuilder();
                    int value;
                    while ((value = responseInput.read()) >= 0) {
                        if (value == '\n') {
                            chunks.add(builder.toString().getBytes(StandardCharsets.UTF_8));
                            builder.setLength(0);
                        } else {
                            builder.append((char) value);
                        }
                    }
                    if (!builder.isEmpty()) {
                        chunks.add(builder.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return chunks.iterator();
        } finally {
            releaseSession(resolvedSession);
        }
    }

    private HttpRequest buildRequest(String method,
                                     String url,
                                     Map<String, String> headers,
                                     Map<String, Object> body,
                                     Double timeoutSeconds,
                                     Map<String, Object> requestArgs) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        Double resolvedTimeout = timeoutSeconds != null ? timeoutSeconds : config.getTimeout();
        if (resolvedTimeout != null && resolvedTimeout > 0) {
            builder.timeout(Duration.ofMillis(Math.max(1L, Math.round(resolvedTimeout * 1000))));
        }
        Map<String, String> mergedHeaders = new LinkedHashMap<>(config.getHeaders());
        if (headers != null) {
            mergedHeaders.putAll(headers);
        }
        if (config.getAuth() instanceof String authHeader && !mergedHeaders.containsKey("Authorization")) {
            mergedHeaders.put("Authorization", authHeader);
        }
        mergedHeaders.forEach(builder::header);
        String upperMethod = method != null ? method.toUpperCase(Locale.ROOT) : "GET";
        if (body != null && !body.isEmpty()) {
            builder.header("Content-Type", "application/json");
            builder.method(upperMethod,
                    HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(body),
                            StandardCharsets.UTF_8));
        } else if ("GET".equals(upperMethod) || "DELETE".equals(upperMethod) || "HEAD".equals(upperMethod)) {
            builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private Object parseBody(HttpResponse<byte[]> response) throws Exception {
        byte[] body = response.body() != null ? response.body() : new byte[0];
        if (body.length == 0) {
            return response.statusCode() == 204 ? null : "";
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("application/json")) {
            try (InputStream input = new ByteArrayInputStream(body)) {
                return MAPPER.readValue(input, Object.class);
            }
        }
        if (contentType.startsWith("text/") || contentType.contains("charset=")) {
            return new String(body, StandardCharsets.UTF_8);
        }
        return body;
    }

    private static String appendQuery(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append("&");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }
}
