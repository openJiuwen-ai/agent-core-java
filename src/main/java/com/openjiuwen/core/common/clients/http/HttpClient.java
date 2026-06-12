/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseClient;
import com.openjiuwen.core.common.clients.SessionConfig;
import com.openjiuwen.core.foundation.tool.service_api.ParserRegistry;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * HTTP client with session management and connection pooling.
 *
 * <p>Mirrors Python's {@code HttpClient} in
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 */
public class HttpClient extends BaseClient {

    private SessionConfig config;
    private HttpSessionManager sessionManager;
    private boolean reuseSession = true;
    private HttpSession session;
    private boolean closed;

    public HttpClient() {
        this((SessionConfig) null, true);
    }

    public HttpClient(SessionConfig config) {
        this(config, true);
    }

    public HttpClient(SessionConfig config, boolean reuseSession) {
        super();
        this.config = normalizeConfig(config);
        this.sessionManager = getHttpSessionManager();
        this.reuseSession = reuseSession;
    }

    public HttpClient(Map<String, Object> config) {
        this(new SessionConfig(config), true);
    }

    public static String getClientName() {
        return "http";
    }

    public static HttpSessionManager getHttpSessionManager() {
        return HttpSessionManager.getHttpSessionManager();
    }

    public SessionConfig getSessionConfig() {
        return config;
    }

    public boolean isReuseSession() {
        return reuseSession;
    }

    public boolean isClosed() {
        return closed;
    }

    public CompletableFuture<Map<String, Object>> get(String url) {
        return get(url, null, RequestOptions.defaults());
    }

    public CompletableFuture<Map<String, Object>> get(String url, Map<String, ?> params, RequestOptions options) {
        return request("GET", url, options.withParams(params));
    }

    public CompletableFuture<Map<String, Object>> post(String url, Map<String, ?> body) {
        return request("POST", url, RequestOptions.defaults().withJson(body));
    }

    public CompletableFuture<Map<String, Object>> put(String url, Map<String, ?> body) {
        return request("PUT", url, RequestOptions.defaults().withJson(body));
    }

    public CompletableFuture<Map<String, Object>> delete(String url) {
        return request("DELETE", url, RequestOptions.defaults());
    }

    public CompletableFuture<Map<String, Object>> patch(String url, Map<String, ?> body) {
        return request("PATCH", url, RequestOptions.defaults().withJson(body));
    }

    public CompletableFuture<Map<String, Object>> head(String url) {
        return request("HEAD", url, RequestOptions.defaults());
    }

    public CompletableFuture<Map<String, Object>> options(String url) {
        return request("OPTIONS", url, RequestOptions.defaults());
    }

    public CompletableFuture<Map<String, Object>> request(String method, String url, RequestOptions options) {
        return acquireSession().thenCompose(acquired -> {
            RequestOptions effectiveOptions = options == null ? RequestOptions.defaults() : options;
            HttpRequest request = buildHttpRequest(method, url, effectiveOptions);
            return acquired.session().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> responseToMap(response, effectiveOptions.chunked(),
                            effectiveOptions.responseBytesSizeLimit()))
                    .whenComplete((ignored, error) -> releaseSession(acquired).join());
        });
    }

    public CompletableFuture<List<Object>> streamGet(String url, RequestOptions options) {
        return streamRequest("GET", url, options == null ? RequestOptions.defaults() : options);
    }

    public CompletableFuture<List<Object>> streamPost(String url, Map<String, ?> body, RequestOptions options) {
        RequestOptions effectiveOptions = options == null ? RequestOptions.defaults() : options;
        return streamRequest("POST", url, effectiveOptions.withJson(body));
    }

    public CompletableFuture<List<Object>> streamRequest(String method, String url, RequestOptions options) {
        RequestOptions effectiveOptions = options == null ? RequestOptions.defaults() : options;
        return acquireSession().thenCompose(acquired -> {
            HttpRequest request = buildHttpRequest(method, url, effectiveOptions);
            return acquired.session().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> splitStreamBody(response.body(), effectiveOptions))
                    .whenComplete((ignored, error) -> releaseSession(acquired).join());
        });
    }

    @Override
    public void initialize(Map<String, Object> kwargs) {
        super.initialize(kwargs);
        this.config = new SessionConfig(kwargs);
        this.sessionManager = getHttpSessionManager();
        this.reuseSession = true;
        this.session = null;
        this.closed = false;
    }

    @Override
    public CompletableFuture<Boolean> close() {
        if (closed) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        CompletableFuture<Void> releaseFuture = CompletableFuture.completedFuture(null);
        if (reuseSession && session != null) {
            releaseFuture = sessionManager.releaseSession(session.config());
            session = null;
        }
        closed = true;
        return releaseFuture.thenApply(ignored -> Boolean.TRUE);
    }

    @Override
    public HttpClient enter() {
        return this;
    }

    public Map<String, Object> buildRequestKwargs(
            Map<String, ?> headers,
            Double timeout,
            Map<String, ?> timeoutArgs,
            Map<String, ?> kwargs) {
        Map<String, Object> requestKwargs = new LinkedHashMap<>();
        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        if (config.getHeaders() != null) {
            mergedHeaders.putAll(config.getHeaders());
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && value != null) {
                    mergedHeaders.put(key, String.valueOf(value));
                }
            });
        }
        requestKwargs.put("headers", mergedHeaders.isEmpty() ? null : mergedHeaders);
        if (kwargs != null) {
            requestKwargs.putAll(kwargs);
        }
        if (timeoutArgs != null && !timeoutArgs.isEmpty()) {
            requestKwargs.put("timeout", new LinkedHashMap<>(timeoutArgs));
        } else if (timeout != null) {
            requestKwargs.put("timeout", Map.of("total", timeout));
        }
        return requestKwargs;
    }

    private CompletableFuture<HttpSession> acquireSession() {
        if (reuseSession) {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("HttpClient is closed"));
            }
            synchronized (this) {
                if (session != null && !session.isClosed()) {
                    return CompletableFuture.completedFuture(session);
                }
            }
            return sessionManager.acquire(config).thenApply(lease -> {
                synchronized (this) {
                    session = lease.resource();
                    return session;
                }
            });
        }
        return sessionManager.acquire(config).thenApply(lease -> lease.resource());
    }

    private CompletableFuture<Void> releaseSession(HttpSession acquired) {
        if (reuseSession) {
            return CompletableFuture.completedFuture(null);
        }
        return sessionManager.releaseSession(acquired.config());
    }

    private HttpRequest buildHttpRequest(String method, String url, RequestOptions options) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(appendParams(url, options.params())));
        Double timeout = options.timeout() != null ? options.timeout() : config.getTimeout();
        if (timeout != null) {
            builder.timeout(seconds(timeout));
        }
        buildHeaders(options.headers()).forEach(builder::setHeader);
        String normalizedMethod = method == null ? "GET" : method.toUpperCase();
        HttpRequest.BodyPublisher publisher = bodyPublisher(options);
        builder.method(normalizedMethod, publisher);
        return builder.build();
    }

    private Map<String, String> buildHeaders(Map<String, ?> requestHeaders) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (config.getHeaders() != null) {
            merged.putAll(config.getHeaders());
        }
        if (requestHeaders != null) {
            requestHeaders.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.put(key, String.valueOf(value));
                }
            });
        }
        return merged;
    }

    private HttpRequest.BodyPublisher bodyPublisher(RequestOptions options) {
        Object json = options.json();
        if (json != null) {
            return HttpRequest.BodyPublishers.ofString(String.valueOf(json), StandardCharsets.UTF_8);
        }
        Object body = options.body();
        if (body != null) {
            return HttpRequest.BodyPublishers.ofString(String.valueOf(body), StandardCharsets.UTF_8);
        }
        return HttpRequest.BodyPublishers.noBody();
    }

    private Map<String, Object> responseToMap(HttpResponse<byte[]> response, boolean chunked, int sizeLimit) {
        byte[] body = response.body() == null ? new byte[0] : response.body();
        if (chunked && body.length > sizeLimit) {
            throw new IllegalArgumentException("Response too large: " + body.length + " > " + sizeLimit);
        }
        Map<String, String> headers = flattenHeaders(response.headers().map());
        Object content = parseBody(headers, body, response.statusCode(), chunked);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", response.statusCode());
        result.put("data", content);
        result.put("url", String.valueOf(response.uri()));
        result.put("headers", headers);
        result.put("reason", reason(response.statusCode()));
        return result;
    }

    private Object parseBody(Map<String, String> headers, byte[] body, int statusCode, boolean chunked) {
        if (chunked) {
            return ParserRegistry.getInstance().parse(headers, body, statusCode);
        }
        String contentType = headers.getOrDefault("content-type", "");
        if (contentType.contains("application/json") || contentType.startsWith("text/")) {
            return ParserRegistry.getInstance().parse(headers, body, statusCode);
        }
        return body;
    }

    private List<Object> splitStreamBody(byte[] body, RequestOptions options) {
        byte[] data = body == null ? new byte[0] : body;
        int chunkSize = Math.max(1, options.chunkSize());
        List<Object> chunks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int end = Math.min(data.length, offset + chunkSize);
            byte[] chunk = java.util.Arrays.copyOfRange(data, offset, end);
            Function<byte[], Object> callback = options.onStreamReceived();
            chunks.add(callback == null ? chunk : callback.apply(chunk));
        }
        return chunks;
    }

    private static SessionConfig normalizeConfig(SessionConfig config) {
        return config == null ? new SessionConfig() : config;
    }

    private static Duration seconds(Double value) {
        return Duration.ofMillis(Math.max(0L, Math.round(value * 1000.0d)));
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

    private static Map<String, String> flattenHeaders(Map<String, List<String>> values) {
        Map<String, String> flattened = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> flattened.put(key.toLowerCase(), String.join(",", value)));
        }
        return flattened;
    }

    private static String reason(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "";
        };
    }

    /**
     * HTTP request options matching Python request kwargs.
     *
     * <p>Mirrors Python's request keyword arguments in
     * {@code openjiuwen/core/common/clients/http_client.py}.</p>
     */
    public record RequestOptions(
            Map<String, ?> headers,
            Double timeout,
            Map<String, ?> timeoutArgs,
            Map<String, ?> params,
            Object json,
            Object body,
            boolean chunked,
            int chunkSize,
            int responseBytesSizeLimit,
            Function<byte[], Object> onStreamReceived) {

        public static RequestOptions defaults() {
            return new RequestOptions(null, null, null, null, null, null, false, 1024, 10 * 1024 * 1024, null);
        }

        public RequestOptions withParams(Map<String, ?> value) {
            return new RequestOptions(headers, timeout, timeoutArgs, value, json, body, chunked, chunkSize,
                    responseBytesSizeLimit, onStreamReceived);
        }

        public RequestOptions withJson(Object value) {
            return new RequestOptions(headers, timeout, timeoutArgs, params, value, body, chunked, chunkSize,
                    responseBytesSizeLimit, onStreamReceived);
        }

        public RequestOptions withHeaders(Map<String, ?> value) {
            return new RequestOptions(value, timeout, timeoutArgs, params, json, body, chunked, chunkSize,
                    responseBytesSizeLimit, onStreamReceived);
        }

        public RequestOptions withTimeout(Double value) {
            return new RequestOptions(headers, value, timeoutArgs, params, json, body, chunked, chunkSize,
                    responseBytesSizeLimit, onStreamReceived);
        }

        public RequestOptions withChunking(boolean useChunking, int size, Function<byte[], Object> callback) {
            return new RequestOptions(headers, timeout, timeoutArgs, params, json, body, useChunking, size,
                    responseBytesSizeLimit, callback);
        }

        public RequestOptions withResponseBytesSizeLimit(int value) {
            return new RequestOptions(headers, timeout, timeoutArgs, params, json, body, chunked, chunkSize,
                    value, onStreamReceived);
        }
    }
}
