/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelCircuitBreaker;
import com.openjiuwen.core.foundation.llm.ModelCircuitBreakerSnapshot;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * UT-S: streaming first-segment retry boundary and error frame surfacing on
 * the OpenAI client family.
 *
 * @since 2026-10-19
 */
@Isolated
class ModelCallRetryFlowTest {
    @Test
    void exhaustedRetriesCountOnceAndFastFailPreservesSnapshot() throws Exception {
        try (StubServer server = new StubServer().enqueue(503, errorFrame("service unavailable"))) {
            Model model = new Model(client(server, 2, new ModelCircuitBreaker(1, 60_000L)));
            Model otherModel = new Model(client(server));
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 0));

            assertThatThrownBy(() -> model.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(RuntimeException.class);
            assertThat(server.requests).hasSize(3);
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(true, 1));
            assertThat(otherModel.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 0));

            assertThatThrownBy(() -> model.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(RuntimeException.class);
            assertThat(server.requests).hasSize(3);
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(true, 1));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 402, 400})
    void permanentHttpFailurePreservesCountAndSuccessResets(int status) throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(429, errorFrame("rate limit reached"))
            .enqueue(status, errorFrame("context_length_exceeded"))
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}")) {
            Model model = new Model(client(server, 0, new ModelCircuitBreaker(3, 60_000L)));

            assertThatThrownBy(() -> model.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(RuntimeException.class);
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 1));
            assertThatThrownBy(() -> model.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(RuntimeException.class);
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 1));

            assertThat(model.invoke("hello").toCompletableFuture().join().getContent()).isEqualTo("ok");
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 0));
            assertThat(server.requests).hasSize(3);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredFacadeAndCompatibleAliasExposeLiveSnapshot(boolean isCompatibleAlias) throws Exception {
        try (StubServer server = new StubServer().enqueue(503, errorFrame("service unavailable"))) {
            ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("gpt-test").build();
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI).apiKey("sk-test").apiBase(server.baseUrl())
                .verifySsl(false).maxRetries(0).build();
            Model model = isCompatibleAlias
                ? new Model(new OpenAiCompatibleModelClient(requestConfig, clientConfig))
                : new Model(clientConfig, requestConfig);

            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 0));
            assertThatThrownBy(() -> model.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(RuntimeException.class);
            assertThat(model.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(false, 1));
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void stream_openPhase429_retriedAndStreams() throws Exception {
        // Given a 429 at the open phase then a good stream, When iterating,
        // Then the whole call is re-initiated once and chunks flow
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
    void stream_errorFrameAfterFirstSegment_neverRetried() throws Exception {
        // Given a stream that yields one chunk and then an error frame, When iterating,
        // Then the error surfaces without a whole-call retry
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
    void stream_errorFrameBeforeFirstSegment_retried() throws Exception {
        // Given an error frame before any segment then a good stream, When iterating,
        // Then the whole call is re-initiated once
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
    void stream_errorFrameAfterFirstSegment_carriesSegmentFlag() throws Exception {
        try (StubServer server = new StubServer()
            .enqueue(200, streamBodyWithoutDone(chunkFrame("partial"), errorFrame("rate limit hit")))) {
            Iterator<AssistantMessageChunk> iterator = client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            iterator.next();
            assertThatThrownBy(iterator::next)
                .isInstanceOf(BaseError.class)
                .satisfies(error -> {
                    if (error instanceof BaseError typed
                        && typed.getDetails() instanceof Map<?, ?> details) {
                        assertThat(details.get(ModelCallClassification.DETAIL_FIRST_SEGMENT))
                            .isEqualTo(Boolean.TRUE);
                    } else {
                        fail("classified BaseError with details expected");
                    }
                });
        }
    }

    @Test
    void stream_errorFrameNotSwallowed_surfacesTypedError() throws Exception {
        // Given an error frame with a non-retryable shape, When iterating,
        // Then it surfaces as a typed error instead of a silent empty stream
        try (StubServer server = new StubServer()
            .enqueue(200, streamBodyWithoutDone(errorFrame("invalid api key provided")))) {
            Iterator<AssistantMessageChunk> iterator = client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThatThrownBy(iterator::hasNext).isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void stream_authFailed_sanitizedAndTyped() throws Exception {
        // UT-E04: the surfaced message must be sanitized and carry the auth code
        try (StubServer server = new StubServer()
            .enqueue(401, "{\"error\":{\"message\":\"bad key sk-live-secret-123\"}}")) {
            assertThatThrownBy(() -> client(server).stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> {
                    if (!(error instanceof BaseError typed)) {
                        fail("BaseError expected but got " + error.getClass().getName());
                        return;
                    }
                    assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_AUTH_FAILED);
                    assertThat(typed.getMessage()).contains("[REDACTED]");
                    assertThat(typed.getMessage()).doesNotContain("sk-live-secret-123");
                });
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void stream_persistentWindowErrorFrames_tripsBreaker() throws Exception {
        // Given a breaker with threshold 1 and persistent 429 error frames, When the
        // first-segment window exhausts the budget, Then the terminal window failure
        // reaches the breaker and the next call fails fast without an HTTP request
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, 60_000L);
        try (StubServer server = new StubServer()
            .enqueue(200, streamBodyWithoutDone(errorFrame("rate limit reached")))) {
            OpenAIModelClient client = client(server, 1, breaker);
            Iterator<AssistantMessageChunk> iterator = client.stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThatThrownBy(iterator::hasNext).isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(2);
            assertThat(client.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(true, 1));
            assertThatThrownBy(() -> client.stream(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(2);
            assertThat(client.getCircuitBreakerSnapshot()).contains(new ModelCircuitBreakerSnapshot(true, 1));
        }
    }

    @Test
    void invoke_officialSignalDeath_rebuildsFieldAndSurfacesWithoutResend() throws Exception {
        // Given a field client that always fails with the JDK's official selector-dead signal,
        // When invoking, Then the field is rebuilt, the failure surfaces with zero wire
        // traffic (no in-call resend — the official signal can fire post-write), and the next
        // call recovers on the rebuilt client
        ModelHttpClients.clearSharedClientsForTesting();
        try (StubServer server = new StubServer()
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"recovered\"}}]}")) {
            OpenAIModelClient noRetry = client(server, 0, new ModelCircuitBreaker());
            FailingHttpClient dead = new FailingHttpClient("selector manager closed");
            noRetry.replaceHttpClientForTesting(dead);

            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(server.requests).isEmpty();
            assertThat(noRetry.httpClientForTesting()).isNotSameAs(dead);

            AssistantMessage recovered = noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThat(recovered.getContentAsString()).isEqualTo("recovered");
            assertThat(server.requests).hasSize(1);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void invoke_unrecognizedFailure_keepsFieldClientWithoutResend() throws Exception {
        // Given a field client failing with an unrecognized wording (ALIVE verdict), When
        // invoking twice, Then the field client stays in place (no rebuild) and failures
        // surface with zero wire traffic
        ModelHttpClients.clearSharedClientsForTesting();
        try (StubServer server = new StubServer()
            .enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ignored\"}}]}")) {
            OpenAIModelClient noRetry = client(server, 0, new ModelCircuitBreaker());
            FailingHttpClient alive = new FailingHttpClient("connection reset by peer");
            noRetry.replaceHttpClientForTesting(alive);

            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThatThrownBy(() -> noRetry.invoke(
                "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(noRetry.httpClientForTesting()).isSameAs(alive);
            assertThat(server.requests).isEmpty();
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    private static OpenAIModelClient client(StubServer server) {
        return client(server, 2, new ModelCircuitBreaker());
    }

    private static OpenAIModelClient client(StubServer server, int maxRetries, ModelCircuitBreaker breaker) {
        return new OpenAIModelClient(
            ModelRequestConfig.builder().modelName("gpt-test").build(),
            ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase(server.baseUrl())
                .verifySsl(false)
                .maxRetries(maxRetries)
                .build(),
            new ModelCallRetrier(delay -> { }, () -> 0.0D),
            breaker);
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

    // A client whose sends always fail with the given wording, so OAI hook tests can drive
    // the eviction/rebuild path: "selector manager closed" exercises the official-signal
    // path, any other wording exercises the ALIVE verdict (no rebuild).
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
