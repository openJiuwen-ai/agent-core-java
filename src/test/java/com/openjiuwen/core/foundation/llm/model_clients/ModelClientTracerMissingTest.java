/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/foundation/llm/test_model_client_tracer.py}.</p>
 */
class ModelClientTracerMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void openAiInvokeCallsTracerRecordDataWithResult() throws Exception {
        Map<String, Object> response = responsePayload("Test response");

        try (MockOpenAiServer server = new MockOpenAiServer(json(response))) {
            OpenAIModelClient client = openAiClient(server.baseUrl());
            RecordingTracer tracer = new RecordingTracer();

            AssistantMessage result = client.invoke(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("tracer_record_data", tracer)
            );

            assertThat(result.getContent()).isEqualTo("Test response");
            assertThat(tracer.records).hasSize(2);
            assertThat(tracer.records.get(0)).containsKey("llm_params");
            assertThat(tracer.records.get(1)).containsEntry("llm_response", result);
        }
    }

    @Test
    void openAiInvokeDoesNotTraceAuthorizationOverrideInExtraHeaders() throws Exception {
        Map<String, Object> response = responsePayload("Test response");
        RecordingTracer tracer = new RecordingTracer();
        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Authorization", "Bearer dynamic-secret");
        requestHeaders.put("X-Trace", "visible-trace");

        try (MockOpenAiServer server = new MockOpenAiServer(json(response))) {
            OpenAIModelClient client = openAiClient(server.baseUrl());

            AssistantMessage result = client.invoke(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(
                            "tracer_record_data", tracer,
                            "custom_headers", requestHeaders
                    )
            );

            assertThat(result.getContent()).isEqualTo("Test response");
            assertThat(tracer.records).isNotEmpty();
            Map<?, ?> llmParams = (Map<?, ?>) tracer.records.get(0).get("llm_params");
            Map<?, ?> tracedHeaders = (Map<?, ?>) llmParams.get("extra_headers");
            assertThat(tracedHeaders.get("X-Trace")).isEqualTo("visible-trace");
            assertThat(tracedHeaders.keySet())
                    .as("Authorization header key should not be traced")
                    .noneMatch(key -> "Authorization".equalsIgnoreCase(String.valueOf(key)));
            assertThat(tracedHeaders.values()).noneMatch(value -> "Bearer dynamic-secret".equals(value));
        }
    }

    @Test
    void openAiStreamDoesNotTraceAuthorizationOverrideInExtraHeaders() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(streamPayload("Hello", "stop")),
                "data: [DONE]");
        RecordingTracer tracer = new RecordingTracer();
        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Authorization", "Bearer stream-secret");
        requestHeaders.put("X-Trace", "visible-stream-trace");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = openAiClient(server.baseUrl());

            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(
                            "tracer_record_data", tracer,
                            "custom_headers", requestHeaders
                    )
            ));

            assertThat(chunks).isNotEmpty();
            assertThat(tracer.records).hasSize(2);
            Map<?, ?> llmParams = (Map<?, ?>) tracer.records.get(0).get("llm_params");
            Map<?, ?> tracedHeaders = (Map<?, ?>) llmParams.get("extra_headers");
            assertThat(tracedHeaders.get("X-Trace")).isEqualTo("visible-stream-trace");
            assertThat(tracedHeaders.keySet())
                    .as("Authorization header key should not be traced")
                    .noneMatch(key -> "Authorization".equalsIgnoreCase(String.valueOf(key)));
            assertThat(tracedHeaders.values()).noneMatch(value -> "Bearer stream-secret".equals(value));
        }
    }

    @Test
    void openAiStreamAccumulatesFinalMessageAndCallsTracer() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(streamPayload("Hello", null)),
                "data: " + json(streamPayload(" ", null)),
                "data: " + json(streamPayload("world", null)),
                "data: " + json(streamPayload("!", "stop")),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = openAiClient(server.baseUrl());
            RecordingTracer tracer = new RecordingTracer();

            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("tracer_record_data", tracer)
            ));

            assertThat(chunks).hasSize(4);
            assertThat(tracer.records).hasSize(2);
            assertThat(tracer.records.get(0)).containsKey("llm_params");
            AssistantMessageChunk finalMessage =
                    (AssistantMessageChunk) tracer.records.get(1).get("llm_response");
            assertThat(finalMessage.getContent()).isEqualTo("Hello world!");
        }
    }

    @Test
    void openAiStreamTracerKeepsUsageChunkAfterStopWhenFullyConsumed() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(streamPayload("Hello", "stop")),
                "data: " + json(Map.of(
                        "usage", Map.of("prompt_tokens", 2, "completion_tokens", 3, "total_tokens", 5),
                        "choices", List.of())),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = openAiClient(server.baseUrl());
            RecordingTracer tracer = new RecordingTracer();

            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("tracer_record_data", tracer)
            ));

            assertThat(chunks).hasSize(2);
            AssistantMessageChunk finalMessage = lastTracedResponse(tracer);
            assertThat(finalMessage.getContent()).isEqualTo("Hello");
            assertThat(finalMessage.getUsageMetadata()).isNotNull();
            assertThat(finalMessage.getUsageMetadata().getTotalTokens()).isEqualTo(5);
        }
    }

    @Test
    void openAiStreamTracerCloseBeforeConsumptionDoesNotRecordNullResponse() throws Exception {
        try (MockOpenAiServer server = new MockOpenAiServer("")) {
            OpenAIModelClient client = openAiClient(server.baseUrl());
            RecordingTracer tracer = new RecordingTracer();

            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("tracer_record_data", tracer)
            );

            ((AutoCloseable) iterator).close();

            assertThat(tracer.records).hasSize(1);
            assertThat(tracer.records.get(0)).containsKey("llm_params");
            assertThat(tracer.records)
                    .noneSatisfy(record -> assertThat(record).containsEntry("llm_response", null));
        }
    }

    @Test
    void siliconFlowInvokeCallsTracerRecordDataWithResult() throws Exception {
        RecordingSiliconFlowClient client = new RecordingSiliconFlowClient();
        client.nextJson = json(responsePayload("Test response"));
        RecordingTracer tracer = new RecordingTracer();

        AssistantMessage result = client.invoke(
                List.of(new UserMessage("Hello")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("tracer_record_data", tracer)
        );

        assertThat(result.getContent()).isEqualTo("Test response");
        assertThat(tracer.records).hasSize(2);
        assertThat(tracer.records.get(0)).containsKey("llm_params");
        assertThat(tracer.records.get(1)).containsEntry("llm_response", result);
    }

    @Test
    void siliconFlowStreamAccumulatesFinalMessageAndCallsTracer() throws Exception {
        RecordingSiliconFlowClient client = new RecordingSiliconFlowClient();
        client.nextStreamLines = List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\" \"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"!\"},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]"
        );
        RecordingTracer tracer = new RecordingTracer();

        List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                List.of(new UserMessage("Hello")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("tracer_record_data", tracer)
        ));

        assertThat(chunks).hasSize(4);
        assertThat(tracer.records).hasSize(2);
        assertThat(tracer.records.get(0)).containsKey("llm_params");
        AssistantMessageChunk finalMessage = (AssistantMessageChunk) tracer.records.get(1).get("llm_response");
        assertThat(finalMessage.getContent()).isEqualTo("Hello world!");
    }

    @Test
    void invokeWithoutTracerDoesNotFail() throws Exception {
        Map<String, Object> response = responsePayload("Test response");

        try (MockOpenAiServer server = new MockOpenAiServer(json(response))) {
            OpenAIModelClient client = openAiClient(server.baseUrl());

            AssistantMessage result = client.invoke(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()
            );

            assertThat(result.getContent()).isEqualTo("Test response");
        }
    }

    @Test
    void streamWithoutTracerDoesNotFail() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(streamPayload("Hello", "stop")),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = openAiClient(server.baseUrl());

            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    List.of(new UserMessage("Hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()
            ));

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).getContent()).isEqualTo("Hello");
        }
    }

    private static OpenAIModelClient openAiClient(String apiBase) {
        return new OpenAIModelClient(
                requestConfig("gpt-3.5-turbo"),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .build());
    }

    private static ModelRequestConfig requestConfig(String modelName) {
        return ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(0.7D)
                .build();
    }

    private static ModelClientConfig siliconFlowConfig() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.SILICON_FLOW)
                .apiKey("sk-test")
                .apiBase("https://api.siliconflow.cn/v1")
                .verifySsl(false)
                .build();
    }

    private static Map<String, Object> responsePayload(String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", content);
        message.put("tool_calls", null);
        message.put("reasoning_content", null);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 20);
        usage.put("total_tokens", 30);
        usage.put("prompt_tokens_details", null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("choices", List.of(choice));
        response.put("usage", usage);
        return response;
    }

    private static Map<String, Object> streamPayload(String content, String finishReason) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("content", content);
        delta.put("reasoning_content", null);
        delta.put("tool_calls", null);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);

        return Map.of("choices", List.of(choice));
    }

    private static String json(Map<String, Object> payload) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static List<AssistantMessageChunk> iteratorToList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static AssistantMessageChunk lastTracedResponse(RecordingTracer tracer) {
        return tracer.records.stream()
                .filter(record -> record.containsKey("llm_response"))
                .map(record -> (AssistantMessageChunk) record.get("llm_response"))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static final class RecordingSiliconFlowClient extends SiliconFlowModelClient {
        private String nextJson = "{}";
        private List<String> nextStreamLines = List.of();

        private RecordingSiliconFlowClient() {
            super(requestConfig("gpt-3.5-turbo"), siliconFlowConfig());
        }

        @Override
        protected HttpResult postJson(Map<String, Object> payload, Float timeout) {
            return new HttpResult(200, nextJson);
        }

        @Override
        protected HttpStreamResult postStream(Map<String, Object> payload, Float timeout) {
            return new HttpStreamResult(200, nextStreamLines, String.join("\n", nextStreamLines));
        }
    }

    private static final class RecordingTracer implements Consumer<Map<String, Object>> {
        private final List<Map<String, Object>> records = new ArrayList<>();

        @Override
        public void accept(Map<String, Object> payload) {
            records.add(payload);
        }
    }

    private static final class MockOpenAiServer implements AutoCloseable {
        private final HttpServer server;
        private final String responseBody;

        private MockOpenAiServer(String responseBody) throws IOException {
            this.responseBody = responseBody;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/chat/completions", this::handle);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            OBJECT_MAPPER.readValue(
                    exchange.getRequestBody().readAllBytes(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
