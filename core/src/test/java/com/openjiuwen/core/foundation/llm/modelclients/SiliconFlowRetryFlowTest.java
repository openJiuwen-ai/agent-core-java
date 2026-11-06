/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * SiliconFlow client: same classification-driven retry semantics as the OpenAI
 * family (open phase retry, error frame surfacing, first-segment boundary).
 *
 * @since 2026-10-19
 */
@Isolated
class SiliconFlowRetryFlowTest {
    @Test
    void stream_openPhase429_retriedAndStreams() throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(429, "{\"error\":{\"message\":\"slow down\"}}")
            .enqueue(200, streamBody(chunkFrame("ok")))) {
            Iterator<AssistantMessageChunk> iterator = client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            List<AssistantMessageChunk> chunks = drain(iterator);
            assertThat(chunks).isNotEmpty();
            assertThat(server.requests).hasSize(2);
        }
    }

    @Test
    void invoke_quotaExceeded_neverRetried() throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(402, "{\"error\":{\"message\":\"balance empty\"}}")) {
            assertThatThrownBy(() -> client(server).invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void stream_errorFrameBeforeFirstSegment_retried() throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(200, streamBodyWithoutDone(errorFrame("rate limit reached")))
            .enqueue(200, streamBody(chunkFrame("ok")))) {
            Iterator<AssistantMessageChunk> iterator = client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            List<AssistantMessageChunk> chunks = drain(iterator);
            assertThat(chunks).isNotEmpty();
            assertThat(server.requests).hasSize(2);
        }
    }

    @Test
    void stream_errorFrameAfterFirstSegment_neverRetried() throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(200, streamBodyWithoutDone(chunkFrame("partial"), errorFrame("rate limit hit")))) {
            Iterator<AssistantMessageChunk> iterator = client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThat(iterator.next().getContent()).isEqualTo("partial");
            assertThatThrownBy(iterator::next).isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void stream_errorFrameNotSwallowed_surfacesTypedError() throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(200, streamBodyWithoutDone(errorFrame("invalid api key provided")))) {
            Iterator<AssistantMessageChunk> iterator = client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThatThrownBy(iterator::hasNext).isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);
        }
    }

    private static SiliconFlowModelClient client(StubServer server) {
        return new SiliconFlowModelClient(
            ModelRequestConfig.builder().modelName("sf-test").build(),
            ModelClientConfig.builder()
                .clientProvider(ProviderType.SILICON_FLOW)
                .apiKey("sk-test")
                .apiBase(server.baseUrl())
                .verifySsl(false)
                .maxRetries(2)
                .build(),
            new ModelCallRetrier(delay -> { }, () -> 0.0D));
    }

    private static SiliconFlowModelClient noRetryClient(StubServer server) {
        return new SiliconFlowModelClient(
            ModelRequestConfig.builder().modelName("sf-test").build(),
            ModelClientConfig.builder()
                .clientProvider(ProviderType.SILICON_FLOW)
                .apiKey("sk-test")
                .apiBase(server.baseUrl())
                .verifySsl(false)
                .maxRetries(0)
                .build(),
            new ModelCallRetrier(delay -> { }, () -> 0.0D));
    }

    // Replaces the single pooled entry with a client whose sends always fail with the given
    // wording, so the next call goes through evictIfDead — "selector manager closed" exercises
    // the official-signal path, any other wording exercises the failure-count fallback.
    private static void poisonPooledClient(HttpClient dead) {
        ModelHttpClients.replaceSolePooledClientForTesting(dead);
    }

    // A pooled client whose sends always fail with the given wording, so the next call
    // goes through evictIfDead: "selector manager closed" exercises the official-signal
    // path, any other wording exercises the failure-count fallback.
    private static final class FailingHttpClient extends HttpClient {
        private final String failureMessage;

        private FailingHttpClient(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            throw new IOException(failureMessage);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void invoke_officialSignalDeath_surfacesWithoutResendAndRecoversNext() throws Exception {
        ModelHttpClients.clearSharedClientsForTesting();
        try (StubServer server = new StubServer()
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"first\"}}]}")
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"recovered\"}}]}")) {
            SiliconFlowModelClient noRetry = noRetryClient(server);
            noRetry.invoke("hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());
            poisonPooledClient(new FailingHttpClient("selector manager closed"));

            // The official selector-closed signal can also fire after the request bytes
            // reached the wire (JDK 17 SocketTube post-write check), so resending could
            // duplicate a call the server may have billed: the failure surfaces with zero
            // additional wire traffic.
            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);

            // The dead entry is evicted; the next call recovers on a fresh pooled client.
            AssistantMessage recovered = noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThat(recovered.getContentAsString()).isEqualTo("recovered");
            assertThat(server.requests).hasSize(2);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void invoke_thresholdEviction_surfacesWithoutResendAndRecoversNext() throws Exception {
        ModelHttpClients.clearSharedClientsForTesting();
        try (StubServer server = new StubServer()
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"first\"}}]}")
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"recovered\"}}]}")) {
            SiliconFlowModelClient noRetry = noRetryClient(server);
            noRetry.invoke("hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());
            poisonPooledClient(new FailingHttpClient("connection reset"));

            // Failures 1 and 2: below the threshold, the error surfaces without wire traffic.
            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            // Failure 3 reaches the threshold: the entry is evicted, but the failure may
            // already have been processed server-side, so no in-call resend happens.
            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);

            assertThat(server.requests).hasSize(1);

            AssistantMessage recovered = noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThat(recovered.getContentAsString()).isEqualTo("recovered");
            assertThat(server.requests).hasSize(2);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    private static List<AssistantMessageChunk> drain(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);
        return chunks;
    }

    private static String chunkFrame(String content) {
        return "{\"choices\":[{\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}";
    }

    private static String errorFrame(String message) {
        return "{\"error\":{\"message\":\"" + message + "\"}}";
    }

    private static String streamBody(String... frames) {
        return streamBodyWithoutDone(frames) + "data: [DONE]\n\n";
    }

    private static String streamBodyWithoutDone(String... frames) {
        StringBuilder body = new StringBuilder();
        for (String frame : frames) {
            body.append("data: ").append(frame).append("\n\n");
        }
        return body.toString();
    }

    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;
        private final Deque<Map<String, Object>> responses = new ArrayDeque<>();
        private final List<String> requests = new ArrayList<>();

        private StubServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/chat/completions", this::handle);
            this.server.start();
        }

        private StubServer enqueue(int status, String body) {
            responses.add(Map.of("status", status, "body", body));
            return this;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            requests.add(exchange.getRequestMethod());
            Map<String, Object> response = responses.size() > 1 ? responses.poll() : responses.peek();
            Object statusValue = response.get("status");
            Object bodyValue = response.get("body");
            if (!(statusValue instanceof Integer status) || !(bodyValue instanceof String bodyText)) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            byte[] bytes = bodyText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
