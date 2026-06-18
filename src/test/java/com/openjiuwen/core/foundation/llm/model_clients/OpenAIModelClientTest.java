/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for {@link OpenAIModelClient}.
 *
 * <p>Mirrors Python's {@code OpenAIModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
 */
class OpenAIModelClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void buildRequestParamsDropsTopPOnlyForOpenAiApiBase() {
        OpenAIModelClient openAiClient = client("https://api.openai.com/v1");
        OpenAIModelClient compatibleClient = client("https://compatible.example.test/v1");

        Map<String, Object> openAiParams = openAiClient.buildPreparedParams(
                "hello",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null);
        Map<String, Object> compatibleParams = compatibleClient.buildPreparedParams(
                "hello",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null);

        assertThat(openAiParams).containsEntry("temperature", 0.95D);
        assertThat(openAiParams).doesNotContainKey("top_p");
        assertThat(compatibleParams).containsEntry("temperature", 0.95D)
                .containsEntry("top_p", 0.1D);
    }

    @Test
    void invokeMergesHeadersMovesReturnTokenIdsAndParsesResponse() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("prompt_token_ids", List.of(10, 11));
        response.put("usage", Map.of(
                "prompt_tokens", 3,
                "completion_tokens", 4,
                "total_tokens", 7,
                "prompt_tokens_details", Map.of("cached_tokens", 2),
                "usage_cost", Map.of("input_cost", 0.1D, "output_cost", 0.2D, "total_cost", 0.3D)));
        response.put("choices", List.of(Map.of(
                "token_ids", List.of(20, 21),
                "logprobs", Map.of("content", List.of("lp")),
                "message", Map.of(
                        "content", "answer",
                        "reasoning_content", "because",
                        "tool_calls", List.of(Map.of(
                                "id", "call-1",
                                "function", Map.of("name", "lookup", "arguments", "{\"q\":1}")))))));

        try (MockOpenAiServer server = new MockOpenAiServer(json(response))) {
            OpenAIModelClient client = client(server.baseUrl());
            Map<String, Object> extraBody = new LinkedHashMap<>();
            extraBody.put("guided_choice", "A");
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("return_token_ids", true);
            kwargs.put("extra_body", extraBody);
            kwargs.put("custom_headers", Map.of("X-Trace", "request", "Authorization", "ignored"));

            AssistantMessage message = client.invoke(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new PrefixParser(),
                    null,
                    kwargs);

            assertThat(header(server.lastHeaders, "Authorization")).isEqualTo("Bearer sk-test");
            assertThat(header(server.lastHeaders, "X-Base")).isEqualTo("base");
            assertThat(header(server.lastHeaders, "X-Trace")).isEqualTo("request");
            assertThat(server.lastBody).containsEntry("return_token_ids", true)
                    .containsEntry("guided_choice", "A");
            assertThat(server.lastBody).doesNotContainKey("extra_body");
            assertThat(message.getContent()).isEqualTo("answer");
            assertThat(message.getReasoningContent()).isEqualTo("because");
            assertThat(message.getParserContent()).isEqualTo("parsed:answer");
            assertThat(message.getPromptTokenIds()).containsExactly(10, 11);
            assertThat(message.getCompletionTokenIds()).containsExactly(20, 21);
            assertThat(message.getLogprobs()).isEqualTo(Map.of("content", List.of("lp")));
            assertThat(message.getUsageMetadata().getCacheTokens()).isEqualTo(2);
            assertThat(message.getUsageMetadata().getTotalCost()).isEqualTo(0.3D);
            ToolCall toolCall = message.getToolCalls().get(0);
            assertThat(toolCall.getId()).isEqualTo("call-1");
            assertThat(toolCall.getName()).isEqualTo("lookup");
            assertThat(toolCall.getIndex()).isEqualTo(0);
        }
    }

    @Test
    void streamAddsIncludeUsageAndKeepsUsageOnlyTokenIdChunk() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "prompt_token_ids", List.of(1, 2),
                        "choices", List.of(Map.of(
                                "delta", Map.of(
                                        "content", "hel",
                                        "token_ids", List.of(30),
                                        "reasoning_content", "r1"),
                                "logprobs", Map.of("a", 1),
                                "finish_reason", "null")))),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("content", "lo"),
                                "token_ids", List.of(31),
                                "finish_reason", "stop")))),
                "data: " + json(Map.of(
                        "usage", Map.of("prompt_tokens", 2, "completion_tokens", 2, "total_tokens", 4),
                        "choices", List.of())),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new PrefixParser(),
                    null,
                    new LinkedHashMap<>());
            List<AssistantMessageChunk> chunks = iteratorToList(iterator);

            assertThat(server.lastBody).containsEntry("stream", true);
            assertThat(server.lastBody.get("stream_options"))
                    .isEqualTo(Map.of("include_usage", true));
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).getContent()).isEqualTo("hel");
            assertThat(chunks.get(0).getPromptTokenIds()).containsExactly(1, 2);
            assertThat(chunks.get(0).getCompletionTokenIds()).containsExactly(30);
            assertThat(chunks.get(0).getParserContent()).isEqualTo("parsed:hel");
            assertThat(chunks.get(1).getContent()).isEqualTo("lo");
            assertThat(chunks.get(2).getContent()).isEqualTo("");
            assertThat(chunks.get(2).getUsageMetadata().getTotalTokens()).isEqualTo(4);
        }
    }

    @Test
    void modelClientsFacadeInstantiatesOpenAiProvider() {
        Object created = ModelClients.createModelClient(
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase("https://compatible.example.test/v1")
                        .verifySsl(false)
                        .customHeaders(Map.of("X-Base", "base"))
                        .build(),
                ModelRequestConfig.builder().modelName("gpt-test").build());

        assertInstanceOf(OpenAIModelClient.class, created);
    }

    private static OpenAIModelClient client(String apiBase) {
        return new OpenAIModelClient(
                ModelRequestConfig.builder().modelName("gpt-test").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .customHeaders(Map.of("X-Base", "base"))
                        .build());
    }

    private static String json(Map<String, Object> payload) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static List<AssistantMessageChunk> iteratorToList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> chunks = new java.util.ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Mirrors Python's asynchronous output parser callback in
     * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.
     */
    private static final class PrefixParser extends BaseOutputParser {
        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.completedFuture("parsed:" + inputs);
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
            return List.of().iterator();
        }
    }

    /**
     * Mirrors Python's patched OpenAI API server in
     * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.
     */
    private static final class MockOpenAiServer implements AutoCloseable {
        private final HttpServer server;
        private final String responseBody;
        private Map<String, Object> lastBody;
        private Map<String, String> lastHeaders;

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
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody = OBJECT_MAPPER.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            lastHeaders = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    lastHeaders.put(name, values.get(0));
                }
            });
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
