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
 * 
 * @since 0.1.7
 */
public class HttpClient extends BaseClient {
    /**
     * CLIENT_NAME.
     * 
     * @since 0.1.7
     */
    public static final String CLIENT_NAME = "http";

    /**
     * CLIENT_TYPE.
     * 
     * @since 0.1.7
     */
    public static final String CLIENT_TYPE = "common";

    /**
     * __client_name__.
     * 
     * @since 0.1.7
     */
    public static final String __client_name__ = CLIENT_NAME;

    /**
     * __client_type__.
     * 
     * @since 0.1.7
     */
    public static final String __client_type__ = CLIENT_TYPE;

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SessionConfig config;
    private final HttpSessionManager sessionManager;
    private final boolean isSessionReuseEnabled;
    private volatile HttpSession session;
    private volatile boolean isClosed;

    /**
     * HttpClient.
     * 
     * @since 0.1.7
     */
    public HttpClient() {
        this(new SessionConfig(), true);
    }

    /**
     * HttpClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    public HttpClient(SessionConfig config) {
        this(config, true);
    }

    /**
     * HttpClient.
     * 
     * @param config config
     * @param isSessionReuseEnabled isSessionReuseEnabled
     * @since 0.1.7
     */
    public HttpClient(SessionConfig config, boolean isSessionReuseEnabled) {
        super(Map.of("reuse_session", isSessionReuseEnabled));
        this.config = config != null ? config : new SessionConfig();
        this.isSessionReuseEnabled = isSessionReuseEnabled;
        this.sessionManager = HttpSessionManager.getInstance();
    }

    /**
     * acquireSession.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    protected synchronized HttpSession acquireSession() throws Exception {
        if (isSessionReuseEnabled) {
            if (isClosed) {
                throw new IllegalStateException("HttpClient is isClosed");
            }
            if (session == null || session.isClosed()) {
                session = sessionManager.acquire(config).resource();
            }
            return session;
        }
        return sessionManager.acquire(config).resource();
    }

    /**
     * releaseSession.
     * 
     * @param resolvedSession resolvedSession
     * @throws Exception Exception
     * @since 0.1.7
     */
    protected synchronized void releaseSession(HttpSession resolvedSession) throws Exception {
        if (!isSessionReuseEnabled && resolvedSession != null) {
            sessionManager.release(resolvedSession.getConfig());
        }
    }

    /**
     * close.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public synchronized void close() throws Exception {
        if (isClosed) {
            return;
        }
        if (isSessionReuseEnabled && session != null) {
            sessionManager.release(session.getConfig());
            session = null;
        }
        isClosed = true;
    }

    /**
     * isClosed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * get.
     * 
     * @param url url
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> get(String url) throws Exception {
        return request("GET", url, null, null, null, Map.of());
    }

    /**
     * get.
     * 
     * @param url url
     * @param params params
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> get(String url, Map<String, Object> params) throws Exception {
        return request("GET", appendQuery(url, params), null, null, null, Map.of());
    }

    /**
     * post.
     * 
     * @param url url
     * @param body body
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> post(String url, Map<String, Object> body) throws Exception {
        return request("POST", url, null, body, null, Map.of());
    }

    /**
     * put.
     * 
     * @param url url
     * @param body body
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> put(String url, Map<String, Object> body) throws Exception {
        return request("PUT", url, null, body, null, Map.of());
    }

    /**
     * patch.
     * 
     * @param url url
     * @param body body
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> patch(String url, Map<String, Object> body) throws Exception {
        return request("PATCH", url, null, body, null, Map.of());
    }

    /**
     * delete.
     * 
     * @param url url
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> delete(String url) throws Exception {
        return request("DELETE", url, null, null, null, Map.of());
    }

    /**
     * head.
     * 
     * @param url url
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> head(String url) throws Exception {
        return request("HEAD", url, null, null, null, Map.of());
    }

    /**
     * options.
     * 
     * @param url url
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> options(String url) throws Exception {
        return request("OPTIONS", url, null, null, null, Map.of());
    }

    /**
     * streamGet.
     * 
     * @param url url
     * @param params params
     * @param isChunkedTransfer isChunkedTransfer
     * @param chunkSize chunkSize
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Iterator<Object> streamGet(String url, Map<String, Object> params, boolean isChunkedTransfer, int chunkSize)
            throws Exception {
        return streamRequest("GET", appendQuery(url, params), null, null, null, isChunkedTransfer, chunkSize, Map.of());
    }

    /**
     * streamPost.
     * 
     * @param url url
     * @param body body
     * @param isChunkedTransfer isChunkedTransfer
     * @param chunkSize chunkSize
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Iterator<Object> streamPost(String url, Map<String, Object> body, boolean isChunkedTransfer, int chunkSize)
            throws Exception {
        return streamRequest("POST", url, null, body, null, isChunkedTransfer, chunkSize, Map.of());
    }

    /**
     * request.
     * 
     * @param method method
     * @param url url
     * @param headers headers
     * @param body body
     * @param timeoutSeconds timeoutSeconds
     * @param requestArgs requestArgs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> request(String method, String url, Map<String, String> headers, Map<String, Object> body,
            Double timeoutSeconds, Map<String, Object> requestArgs) throws Exception {
        HttpSession resolvedSession = acquireSession();
        try {
            HttpRequest request = buildRequest(method, url, headers, body, timeoutSeconds, requestArgs);
            HttpResponse<byte[]> response =
                resolvedSession.session().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (config.isRaiseForStatus() && response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "HTTP " + response.statusCode() + ": " + new String(response.body(), StandardCharsets.UTF_8));
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
     * requestAsync.
     * 
     * @param method method
     * @param url url
     * @param headers headers
     * @param body body
     * @param timeoutSeconds timeoutSeconds
     * @param requestArgs requestArgs
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Map<String, Object>> requestAsync(String method, String url, Map<String, String> headers,
            Map<String, Object> body, Double timeoutSeconds, Map<String, Object> requestArgs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request(method, url, headers, body, timeoutSeconds, requestArgs);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * streamRequest.
     * 
     * @param method method
     * @param url url
     * @param headers headers
     * @param body body
     * @param timeoutSeconds timeoutSeconds
     * @param isChunkedTransfer isChunkedTransfer
     * @param chunkSize chunkSize
     * @param requestArgs requestArgs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Iterator<Object> streamRequest(String method, String url, Map<String, String> headers,
            Map<String, Object> body, Double timeoutSeconds, boolean isChunkedTransfer, int chunkSize,
            Map<String, Object> requestArgs) throws Exception {
        HttpSession resolvedSession = acquireSession();
        try {
            HttpRequest request = buildRequest(method, url, headers, body, timeoutSeconds, requestArgs);
            HttpResponse<InputStream> response =
                resolvedSession.session().send(request, HttpResponse.BodyHandlers.ofInputStream());
            InputStream bodyStream = response.body();
            if (bodyStream == null) {
                return List.<Object>of().iterator();
            }
            try (InputStream responseInput = bodyStream) {
                List<Object> chunks = new ArrayList<>();
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
                return chunks.iterator();
            }
        } finally {
            releaseSession(resolvedSession);
        }
    }

    /**
     * buildRequest.
     * 
     * @param method method
     * @param url url
     * @param headers headers
     * @param body body
     * @param timeoutSeconds timeoutSeconds
     * @param requestArgs requestArgs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private HttpRequest buildRequest(String method, String url, Map<String, String> headers, Map<String, Object> body,
            Double timeoutSeconds, Map<String, Object> requestArgs) throws Exception {
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
                    HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        } else if ("GET".equals(upperMethod) || "DELETE".equals(upperMethod) || "HEAD".equals(upperMethod)) {
            builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    /**
     * parseBody.
     * 
     * @param response response
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
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

    /**
     * appendQuery.
     * 
     * @param url url
     * @param params params
     * @return the result
     * @since 0.1.7
     */
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
