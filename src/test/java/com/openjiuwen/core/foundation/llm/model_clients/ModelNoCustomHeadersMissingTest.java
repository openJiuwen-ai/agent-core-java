/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/foundation/llm/test_model_no_custom_headers.py}.</p>
 */
class ModelNoCustomHeadersMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest(name = "invoke without custom headers via {0}")
    @ValueSource(strings = {"model", "init_model", "react_agent"})
    void invokeWithoutCustomHeadersDoesNotSendExtraHeaders(String mode) throws Exception {
        try (MockOpenAiServer server = new MockOpenAiServer(jsonResponse())) {
            OpenAIModelClient client = clientWithoutHeaders(mode, server.baseUrl());

            client.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null, null, Map.of());

            assertNoCustomHeaders(server);
        }
    }

    @ParameterizedTest(name = "stream without custom headers via {0}")
    @ValueSource(strings = {"model", "init_model", "react_agent"})
    void streamWithoutCustomHeadersDoesNotSendExtraHeaders(String mode) throws Exception {
        try (MockOpenAiServer server = new MockOpenAiServer(streamResponse())) {
            OpenAIModelClient client = clientWithoutHeaders(mode, server.baseUrl());

            Iterator<?> iterator = client.stream(
                    List.of(new UserMessage("hello")),
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
            while (iterator.hasNext()) {
                iterator.next();
            }

            assertNoCustomHeaders(server);
        }
    }

    private static OpenAIModelClient clientWithoutHeaders(String mode, String apiBase) {
        return switch (mode) {
            case "model" -> new OpenAIModelClient(requestConfig(), clientConfig(apiBase));
            case "init_model" -> initModelEquivalentClient(apiBase);
            case "react_agent" -> reactAgentEquivalentClient(apiBase);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };
    }

    private static OpenAIModelClient initModelEquivalentClient(String apiBase) {
        return new OpenAIModelClient(
                ModelRequestConfig.builder()
                        .modelName("gpt-4o-mini")
                        .temperature(0.95D)
                        .topP(0.1D)
                        .build(),
                clientConfig(apiBase)
        );
    }

    private static OpenAIModelClient reactAgentEquivalentClient(String apiBase) {
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureModelClient(
                ProviderType.OPEN_AI.getValue(),
                "sk-test",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                false
        );
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(config.getModelClientConfig().getClientProvider())
                .apiKey(config.getModelClientConfig().getApiKey())
                .apiBase(apiBase)
                .verifySsl(config.getModelClientConfig().isVerifySsl())
                .customHeaders(config.getModelClientConfig().getCustomHeaders())
                .build();
        return new OpenAIModelClient(config.getModelConfigObj(), clientConfig);
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder().modelName("gpt-4o-mini").build();
    }

    private static ModelClientConfig clientConfig(String apiBase) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase(apiBase)
                .verifySsl(false)
                .build();
    }

    private static void assertNoCustomHeaders(MockOpenAiServer server) {
        assertThat(header(server.lastHeaders, "Token")).isNull();
        assertThat(header(server.lastHeaders, "UserID")).isNull();
        assertThat(header(server.lastHeaders, "X-Trace")).isNull();
        assertThat(header(server.lastHeaders, "X-Base")).isNull();
        assertThat(header(server.lastHeaders, "Authorization")).isEqualTo("Bearer sk-test");
        assertThat(server.lastBody).doesNotContainKey("extra_headers");
    }

    private static String jsonResponse() throws IOException {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", "ok");
        message.put("tool_calls", null);
        message.put("reasoning_content", null);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", message);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 5);
        usage.put("completion_tokens", 3);
        usage.put("total_tokens", 8);
        usage.put("prompt_tokens_details", null);
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "choices", List.of(choice),
                "usage", usage
        ));
    }

    private static String streamResponse() throws IOException {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("content", "hello");
        delta.put("reasoning_content", null);
        delta.put("tool_calls", null);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("delta", delta);
        choice.put("finish_reason", "stop");
        return "data: " + OBJECT_MAPPER.writeValueAsString(Map.of("choices", List.of(choice)))
                + "\ndata: [DONE]";
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

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
            lastBody = OBJECT_MAPPER.readValue(
                    exchange.getRequestBody().readAllBytes(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            lastHeaders = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    lastHeaders.put(name, values.getFirst());
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
