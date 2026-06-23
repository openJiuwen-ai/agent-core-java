/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.foundation.llm.HeadersHelper;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/foundation/llm/test_model_custom_headers.py}.</p>
 */
class ModelCustomHeadersMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void directModelConstructorSanitizesCustomHeaders() throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Token", "token-custom");
        headers.put("UserID", "user-001");
        headers.put("Content-Length", "blocked");
        headers.put("X-None", null);
        headers.put("", "empty-key");

        try (MockOpenAiServer server = new MockOpenAiServer(jsonResponse("ok"))) {
            OpenAIModelClient client = openAiClient(server.baseUrl(), headers);

            client.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null, null, Map.of());

            assertThat(header(server.lastHeaders, "Token")).isEqualTo("token-custom");
            assertThat(header(server.lastHeaders, "UserID")).isEqualTo("user-001");
            assertThat(header(server.lastHeaders, "Content-Length")).isNotEqualTo("blocked");
            assertThat(header(server.lastHeaders, "X-None")).isNull();
            assertThat(header(server.lastHeaders, "")).isNull();
            assertThat(header(server.lastHeaders, "Authorization")).isEqualTo("Bearer sk-test");
        }
    }

    @Test
    void initModelMergesRequestCustomHeaders() throws Exception {
        Map<String, Object> configHeaders = new LinkedHashMap<>();
        configHeaders.put("Token", "token-init");
        configHeaders.put("UserID", "user-init");
        configHeaders.put("Host", "blocked");

        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("token", "token-req");
        requestHeaders.put("UserID", "user-req");
        requestHeaders.put("Transfer-Encoding", "blocked");
        requestHeaders.put("Authorization", "Bearer blocked");
        requestHeaders.put("X-Empty", "");

        try (MockOpenAiServer server = new MockOpenAiServer(jsonResponse("ok"))) {
            OpenAIModelClient client = openAiClient(server.baseUrl(), configHeaders);

            client.invoke(
                    List.of(new UserMessage("hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("custom_headers", requestHeaders)
            );

            assertThat(header(server.lastHeaders, "Token")).isEqualTo("token-req");
            assertThat(header(server.lastHeaders, "UserID")).isEqualTo("user-req");
            assertThat(header(server.lastHeaders, "Transfer-Encoding")).isNotEqualTo("blocked");
            assertThat(header(server.lastHeaders, "Authorization")).isEqualTo("Bearer sk-test");
            assertThat(header(server.lastHeaders, "X-Empty")).isNull();
        }
    }

    @Test
    void headersMergeIsCaseInsensitiveAndBlocksAuthorization() {
        Map<String, Object> configHeaders = new LinkedHashMap<>();
        configHeaders.put("X-Tenant", "tenant-config");
        configHeaders.put("Authorization", "Bearer blocked-config");
        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("x-tenant", "tenant-request");
        requestHeaders.put("userid", "user-request");
        requestHeaders.put("authorization", "Bearer blocked-request");

        Map<String, String> sentHeaders = OpenAIModelClient.buildRequestHeaders(
                HeadersHelper.buildBaseHeaders(configHeaders),
                requestHeaders
        );

        assertThat(sentHeaders)
                .containsEntry("X-Tenant", "tenant-request")
                .containsEntry("userid", "user-request")
                .doesNotContainKey("Authorization")
                .doesNotContainKey("authorization");
    }

    @Test
    void reactAgentConfigureModelClientPropagatesCustomHeaders() throws Exception {
        Map<String, Object> configHeaders = new LinkedHashMap<>();
        configHeaders.put("Token", "token-react");
        configHeaders.put("UserID", "user-react");
        configHeaders.put("Connection", "blocked");
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureCustomHeaders(configHeaders);
        config.configureModelClient(
                ProviderType.OPEN_AI.getValue(),
                "sk-test",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                false
        );

        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("UserID", "user-override");
        requestHeaders.put("X-Empty", null);

        try (MockOpenAiServer server = new MockOpenAiServer(jsonResponse("ok"))) {
            OpenAIModelClient client = new OpenAIModelClient(
                    config.getModelConfigObj(),
                    withApiBase(config.getModelClientConfig(), server.baseUrl())
            );

            client.invoke(
                    List.of(new UserMessage("hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("custom_headers", requestHeaders)
            );

            assertThat(header(server.lastHeaders, "Token")).isEqualTo("token-react");
            assertThat(header(server.lastHeaders, "UserID")).isEqualTo("user-override");
            assertThat(header(server.lastHeaders, "Connection")).isNotEqualTo("blocked");
            assertThat(header(server.lastHeaders, "X-Empty")).isNull();
        }
    }

    @Test
    void streamInjectsEffectiveHeaders() throws Exception {
        Map<String, Object> configHeaders = new LinkedHashMap<>();
        configHeaders.put("UserID", "user-cfg");
        configHeaders.put("Host", "blocked");
        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("UserID", "user-req");
        requestHeaders.put("Connection", "blocked");

        try (MockOpenAiServer server = new MockOpenAiServer(streamResponse())) {
            OpenAIModelClient client = openAiClient(server.baseUrl(), configHeaders);

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
                    Map.of("custom_headers", requestHeaders)
            );
            while (iterator.hasNext()) {
                iterator.next();
            }

            assertThat(header(server.lastHeaders, "UserID")).isEqualTo("user-req");
            assertThat(header(server.lastHeaders, "Host")).isNotEqualTo("blocked");
            assertThat(header(server.lastHeaders, "Connection")).isNotEqualTo("blocked");
        }
    }

    @Test
    void modelFingerprintStaysStableWithDifferentCustomHeaders() {
        ModelClientConfig cfg1 = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .customHeaders(Map.of("Token", "token-a", "UserID", "user-a"))
                .build();
        ModelClientConfig cfg2 = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .customHeaders(Map.of("Token", "token-b", "UserID", "user-b"))
                .build();

        String key1 = HashUtil.generateKey(cfg1.getApiKey(), cfg1.getApiBase(), cfg1.getClientProvider());
        String key2 = HashUtil.generateKey(cfg2.getApiKey(), cfg2.getApiBase(), cfg2.getClientProvider());

        assertThat(key1).isEqualTo(key2);
    }

    private static OpenAIModelClient openAiClient(String apiBase, Map<String, Object> customHeaders) {
        return new OpenAIModelClient(
                ModelRequestConfig.builder().modelName("gpt-4o-mini").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .customHeaders(customHeaders)
                        .build());
    }

    private static ModelClientConfig withApiBase(ModelClientConfig original, String apiBase) {
        return ModelClientConfig.builder()
                .clientProvider(original.getClientProvider())
                .apiKey(original.getApiKey())
                .apiBase(apiBase)
                .verifySsl(original.isVerifySsl())
                .customHeaders(original.getCustomHeaders())
                .build();
    }

    private static String jsonResponse(String content) throws IOException {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", content);
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
        if (headers == null || name == null) {
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
            OBJECT_MAPPER.readValue(
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
