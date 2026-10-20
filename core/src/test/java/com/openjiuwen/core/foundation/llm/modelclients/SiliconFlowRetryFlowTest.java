/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow client: same classification-driven retry semantics as the OpenAI
 * family (open phase retry, error frame surfacing, first-segment boundary).
 *
 * @since 2026-10-19
 */
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
            int status = (Integer) response.get("status");
            byte[] bytes = ((String) response.get("body")).getBytes(StandardCharsets.UTF_8);
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
