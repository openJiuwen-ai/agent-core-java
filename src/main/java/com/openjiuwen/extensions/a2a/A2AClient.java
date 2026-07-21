/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.singleagent.schema.AgentResult;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal HTTP client for the A2A JSON-RPC transport.
 * 
 * @since 0.1.7
 */
public class A2AClient {
    private final URI endpoint;
    private final HttpClient httpClient;
    private final Map<String, String> authHeaders;

    /**
     * Create an A2A client with a default HTTP client.
     * 
     * @param endpointUrl A2A endpoint URL
     * @since 0.1.7
     */
    public A2AClient(String endpointUrl) {
        this(endpointUrl, HttpClient.newHttpClient(), Map.of());
    }

    /**
     * Create an A2A client with a custom HTTP client.
     * 
     * @param endpointUrl A2A endpoint URL
     * @param httpClient HTTP client
     * @since 0.1.7
     */
    public A2AClient(String endpointUrl, HttpClient httpClient) {
        this(endpointUrl, httpClient, Map.of());
    }

    /**
     * Create an A2A client with a custom HTTP client and auth headers.
     *
     * @param endpointUrl A2A endpoint URL
     * @param httpClient HTTP client
     * @param authHeaders outbound auth headers
     * @since 0.1.7
     */
    public A2AClient(String endpointUrl, HttpClient httpClient, Map<String, String> authHeaders) {
        this.endpoint = URI.create(normalizeEndpoint(endpointUrl));
        this.httpClient = httpClient;
        this.authHeaders = authHeaders == null ? Map.of() : Map.copyOf(authHeaders);
    }

    /**
     * Invoke the remote A2A endpoint once.
     * 
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return agent result
     * @throws Exception when the HTTP call or response parsing fails
     * @since 0.1.7
     */
    public AgentResult invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = A2ATransformer.toJsonRpcRequest(inputs, "SendMessage", requestId);
        HttpRequest request = buildJsonRequest(payload, timeoutSeconds);
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), response.body());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = JsonUtils.getMapper().readValue(response.body(), Map.class);
        return A2ATransformer.fromA2AResponse(body);
    }

    /**
     * Start a streaming A2A call.
     * 
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return iterator over stream chunks
     * @throws Exception when the HTTP call or response parsing fails
     * @since 0.1.7
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = A2ATransformer.toJsonRpcRequest(inputs, "SendStreamingMessage", requestId);
        HttpRequest request = buildJsonRequest(payload, timeoutSeconds);
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        ensureSuccess(response.statusCode(), "<stream>");
        return new SseIterator(response.body());
    }

    /**
     * Reactive version of {@link #invoke(Map, Double)}.
     * 
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return Mono emitting the agent result
     * @since 0.1.7
     */
    public Mono<AgentResult> invokeAsync(Map<String, Object> inputs, Double timeoutSeconds) {
        return ReactiveAdapters.fromCallable(() -> invoke(inputs, timeoutSeconds));
    }

    /**
     * Reactive version of {@link #stream(Map, Double)}.
     * 
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return Flux emitting stream chunks
     * @since 0.1.7
     */
    public Flux<Object> streamAsync(Map<String, Object> inputs, Double timeoutSeconds) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(inputs, timeoutSeconds));
    }

    /**
     * buildJsonRequest.
     * 
     * @param payload payload
     * @param timeoutSeconds timeoutSeconds
     * @return the result
     * @since 0.1.7
     */
    private HttpRequest buildJsonRequest(Map<String, Object> payload, Double timeoutSeconds) {
        Duration timeout =
            timeoutSeconds != null ? Duration.ofMillis(toMillis(timeoutSeconds)) : Duration.ofSeconds(30);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream").timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.safeJsonDumps(payload), StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : authHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    /**
     * toMillis.
     * 
     * @param timeoutSeconds timeoutSeconds
     * @return the result
     * @since 0.1.7
     */
    private static long toMillis(Double timeoutSeconds) {
        return BigDecimal.valueOf(timeoutSeconds).movePointRight(3).longValue();
    }

    /**
     * ensureSuccess.
     * 
     * @param statusCode statusCode
     * @param body body
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static void ensureSuccess(int statusCode, String body) throws IOException {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new IOException("A2A HTTP " + statusCode + ": " + body);
    }

    static String normalizeEndpoint(String url) {
        String normalized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (normalized.endsWith("/a2a/jsonrpc")) {
            return normalized;
        }
        return normalized + "/a2a/jsonrpc";
    }

    private static final class SseIterator implements Iterator<Object>, AutoCloseable {
        private final BufferedReader reader;
        private AgentResult next;
        private boolean isDone;
        private volatile boolean isClosed;

        /**
         * SseIterator.
         * 
         * @param stream stream
         * @since 0.1.7
         */
        private SseIterator(InputStream stream) {
            this.reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        /**
         * close.
         * 
         * @since 0.1.7
         */
        @Override
        public void close() {
            if (isClosed) {
                return;
            }
            isClosed = true;
            isDone = true;
            closeReader();
        }

        /**
         * hasNext.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            if (isDone) {
                return false;
            }
            try {
                Optional<AgentResult> result = readNext();
                if (result.isEmpty()) {
                    isDone = true;
                    closeReader();
                    return false;
                }
                next = result.get();
                return true;
            } catch (IOException ex) {
                isDone = true;
                closeReader();
                throw new IllegalStateException("Failed to read A2A SSE stream", ex);
            }
        }

        /**
         * next.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            AgentResult result = next;
            next = null;
            return result;
        }

        /**
         * readNext.
         * 
         * @return the result
         * @throws IOException IOException
         * @since 0.1.7
         */
        private Optional<AgentResult> readNext() throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring("data:".length()).trim();
                if (json.isBlank()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = JsonUtils.getMapper().readValue(json, Map.class);
                return Optional.of(A2ATransformer.fromA2AResponse(payload));
            }
            return Optional.empty();
        }

        /**
         * closeReader.
         * 
         * @since 0.1.7
         */
        private void closeReader() {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }
    }
}
