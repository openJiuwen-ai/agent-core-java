/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * HTTP-backed upstream client with retry for transient failures.
 * <p>
 * Mirrors Python's {@code HTTPXUpstreamGatewayClient} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.upstream_client}.
 */
public class HttpUpstreamGatewayClient implements UpstreamGatewayClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 409, 425, 429, 500, 502, 503, 504);

    private final GatewayHttpTransport httpTransport;
    private final String llmUrl;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
    private final Duration requestTimeout;

    public HttpUpstreamGatewayClient(GatewayHttpTransport httpTransport, String llmUrl, RetryPolicy retryPolicy) {
        this(httpTransport, llmUrl, retryPolicy, millis -> {
            if (millis > 0L) {
                Thread.sleep(millis);
            }
        }, Duration.ofSeconds(120));
    }

    public HttpUpstreamGatewayClient(GatewayHttpTransport httpTransport,
                                     String llmUrl,
                                     RetryPolicy retryPolicy,
                                     Duration requestTimeout) {
        this(httpTransport, llmUrl, retryPolicy, millis -> {
            if (millis > 0L) {
                Thread.sleep(millis);
            }
        }, requestTimeout);
    }

    HttpUpstreamGatewayClient(GatewayHttpTransport httpTransport,
                              String llmUrl,
                              RetryPolicy retryPolicy,
                              Sleeper sleeper,
                              Duration requestTimeout) {
        this.httpTransport = httpTransport;
        this.llmUrl = trimTrailingSlash(llmUrl);
        this.retryPolicy = retryPolicy != null ? retryPolicy : new RetryPolicy();
        this.sleeper = sleeper;
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(120);
    }

    @Override
    public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
        return requestWithRetry("chat.completions", () -> buildJsonRequest("POST", llmUrl + "/v1/chat/completions", headers, jsonBody));
    }

    @Override
    public GatewayHttpResponse request(String method, String url, Map<String, Object> params, Map<String, String> headers, byte[] content) {
        return requestWithRetry("proxy." + method.toLowerCase(), () -> buildBinaryRequest(method, appendParams(url, params), headers, content));
    }

    private GatewayHttpResponse requestWithRetry(String operation, Supplier<HttpRequest> requestFactory) {
        int attempt = 0;
        while (true) {
            try {
                GatewayHttpResponse response = httpTransport.send(requestFactory.get());
                if (RETRYABLE_STATUS_CODES.contains(response.statusCode()) && attempt < retryPolicy.maxRetries()) {
                    attempt += 1;
                    sleepBeforeRetry(operation, attempt, "status=" + response.statusCode());
                    continue;
                }
                return response;
            } catch (IOException exception) {
                if (attempt >= retryPolicy.maxRetries()) {
                    throw new RuntimeException(exception);
                }
                attempt += 1;
                sleepBeforeRetry(operation, attempt, exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
        }
    }

    private void sleepBeforeRetry(String operation, int attempt, String reason) {
        try {
            sleeper.sleep((long) (retryPolicy.backoffForAttempt(attempt) * 1000L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted for " + operation + ": " + reason, exception);
        }
    }

    private HttpRequest buildJsonRequest(String method, String url, Map<String, String> headers, Map<String, Object> body) {
        try {
            return buildRequest(method, url, headers, OBJECT_MAPPER.writeValueAsBytes(body), true);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize upstream request body", exception);
        }
    }

    private HttpRequest buildBinaryRequest(String method, String url, Map<String, String> headers, byte[] content) {
        return buildRequest(method, url, headers, content != null ? content : new byte[0], false);
    }

    private HttpRequest buildRequest(String method, String url, Map<String, String> headers, byte[] content, boolean json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .method(method.toUpperCase(), HttpRequest.BodyPublishers.ofByteArray(content));
        if (json) {
            builder.header("Content-Type", "application/json");
        }
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder.build();
    }

    private static String appendParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            joiner.add(encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())));
        }
        return url + (url.contains("?") ? "&" : "?") + joiner;
    }

    private static String trimTrailingSlash(String value) {
        String safe = value != null ? value : "";
        while (safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
