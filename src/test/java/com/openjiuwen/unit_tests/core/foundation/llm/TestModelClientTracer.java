/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.SiliconFlowModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for model-client tracer recording.
 * <p>
 * Mirrors Python's
 * {@code agent-core-0.1.12/tests/unit_tests/core/foundation/llm/test_model_client_tracer.py}.
 */
@DisplayName("TestModelClientTracer")
class TestModelClientTracer {

    private static final String CHAT_RESPONSE = """
            {"choices":[{"message":{"content":"Test response","tool_calls":null,"reasoning_content":null},\
            "finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}\
            """;

    @Test
    @DisplayName("OpenAI invoke calls tracer_record_data with result")
    void testOpenAiInvokeCallsTracerRecordDataWithResult() throws Exception {
        withServer(false, server -> {
            Model model = buildModel("OpenAI", server);
            RecordingTracer tracer = new RecordingTracer();

            AssistantMessage result = model.invoke(
                    List.of(new UserMessage("Hello")),
                    null, null, null, null, null, null, null, null,
                    Map.of("tracer_record_data", tracer));

            assertEquals("Test response", result.getContentAsString());
            assertEquals(2, tracer.records.size());
            assertTrue(tracer.records.get(0).containsKey("llm_params"));
            assertTrue(tracer.records.get(1).containsKey("llm_response"));
            AssistantMessage traced = (AssistantMessage) tracer.records.get(1).get("llm_response");
            assertEquals("Test response", traced.getContentAsString());
        });
    }

    @Test
    @DisplayName("OpenAI stream accumulates final message and calls tracer")
    void testOpenAiStreamAccumulatesFinalMessageAndCallsTracer() throws Exception {
        withServer(true, server -> {
            Model model = buildModel("OpenAI", server);
            RecordingTracer tracer = new RecordingTracer();

            Iterator<AssistantMessageChunk> stream = model.stream(
                    List.of(new UserMessage("Hello")),
                    null, null, null, null, null, null, null, null,
                    Map.of("tracer_record_data", tracer));

            List<AssistantMessageChunk> chunks = new ArrayList<>();
            while (stream.hasNext()) {
                chunks.add(stream.next());
            }

            assertEquals(4, chunks.size());
            assertEquals(2, tracer.records.size());
            AssistantMessage traced = (AssistantMessage) tracer.records.get(1).get("llm_response");
            assertEquals("Hello world!", traced.getContentAsString());
        });
    }

    @Test
    @DisplayName("SiliconFlow invoke calls tracer_record_data with result")
    void testSiliconFlowInvokeCallsTracerRecordDataWithResult() throws Exception {
        withServer(false, server -> {
            SiliconFlowModelClient client = buildSiliconFlowClient(server);
            RecordingTracer tracer = new RecordingTracer();

            AssistantMessage result = client.invoke(
                    List.of(new UserMessage("Hello")),
                    null, null, null, null, null, null, null, null,
                    Map.of("tracer_record_data", tracer));

            assertEquals("Test response", result.getContentAsString());
            assertEquals(2, tracer.records.size());
            assertTrue(tracer.records.get(1).containsKey("llm_response"));
            AssistantMessage traced = (AssistantMessage) tracer.records.get(1).get("llm_response");
            assertEquals("Test response", traced.getContentAsString());
        });
    }

    @Test
    @DisplayName("SiliconFlow stream accumulates final message and calls tracer")
    void testSiliconFlowStreamAccumulatesFinalMessageAndCallsTracer() throws Exception {
        withServer(true, server -> {
            SiliconFlowModelClient client = buildSiliconFlowClient(server);
            RecordingTracer tracer = new RecordingTracer();

            Iterator<AssistantMessageChunk> stream = client.stream(
                    List.of(new UserMessage("Hello")),
                    null, null, null, null, null, null, null, null,
                    Map.of("tracer_record_data", tracer));

            List<AssistantMessageChunk> chunks = new ArrayList<>();
            while (stream.hasNext()) {
                chunks.add(stream.next());
            }

            assertEquals(4, chunks.size());
            assertEquals(2, tracer.records.size());
            AssistantMessage traced = (AssistantMessage) tracer.records.get(1).get("llm_response");
            assertEquals("Hello world!", traced.getContentAsString());
        });
    }

    @Test
    @DisplayName("invoke without tracer does not fail")
    void testInvokeWithoutTracerDoesNotFail() throws Exception {
        withServer(false, server -> {
            Model model = buildModel("OpenAI", server);

            AssistantMessage result = model.invoke(
                    List.of(new UserMessage("Hello")),
                    null, null, null, null, null, null, null, null, null);

            assertEquals("Test response", result.getContentAsString());
        });
    }

    @Test
    @DisplayName("stream without tracer does not fail")
    void testStreamWithoutTracerDoesNotFail() throws Exception {
        withServer(true, server -> {
            Model model = buildModel("OpenAI", server);

            Iterator<AssistantMessageChunk> stream = model.stream(
                    List.of(new UserMessage("Hello")),
                    null, null, null, null, null, null, null, null, null);

            List<AssistantMessageChunk> chunks = new ArrayList<>();
            while (stream.hasNext()) {
                chunks.add(stream.next());
            }

            assertTrue(chunks.size() > 0);
            assertEquals("Hello", chunks.get(0).getContentAsString());
        });
    }

    private static Model buildModel(String provider, HttpServer server) {
        return new Model(clientConfig(provider, server), requestConfig());
    }

    private static SiliconFlowModelClient buildSiliconFlowClient(HttpServer server) {
        return new SiliconFlowModelClient(requestConfig(), clientConfig("SiliconFlow", server));
    }

    private static ModelClientConfig clientConfig(String provider, HttpServer server) {
        return ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey("sk-test")
                .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .verifySsl(false)
                .build();
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder()
                .modelName("gpt-3.5-turbo")
                .temperature(Double.valueOf(0.7))
                .build();
    }

    private static void withServer(boolean stream, ThrowingConsumer<HttpServer> testBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            if (stream) {
                writeSse(exchange);
            } else {
                writeJson(exchange, CHAT_RESPONSE);
            }
        });
        server.start();
        try {
            testBody.accept(server);
        } finally {
            server.stop(0);
        }
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void writeSse(HttpExchange exchange) throws IOException {
        String body = """
                data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":" "},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"world"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"!"},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class RecordingTracer implements Consumer<Map<String, Object>> {
        private final List<Map<String, Object>> records = new ArrayList<>();

        @Override
        public void accept(Map<String, Object> payload) {
            records.add(payload);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
