/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for model custom headers behavior.
 *
 * <p>Mirrors Python's tests/unit_tests/core/foundation/llm/test_model_custom_headers.py.</p>
 */
@DisplayName("TestModelCustomHeaders")
class TestModelCustomHeaders {

    @Test
    @DisplayName("Test headers initialization sanitizes config headers")
    void testHeadersInit() throws Exception {
        CapturedRequest captured = invoke(
                baseClientConfig(null, headers(
                        "Token", "token-custom",
                        "UserID", "user-001",
                        "Content-Length", "blocked",
                        "X-None", null,
                        "", "empty-key")),
                null,
                false);

        assertEquals("token-custom", captured.header("Token"));
        assertEquals("user-001", captured.header("UserID"));
        assertTrue(captured.header("Content-Length") == null
                || !"blocked".equals(captured.header("Content-Length")));
        assertFalse(captured.body.contains("extra_headers"));
    }

    @Test
    @DisplayName("Test headers add merges request headers case-insensitively")
    void testHeadersAdd() throws Exception {
        CapturedRequest captured = invoke(
                baseClientConfig(null, headers(
                        "X-Tenant", "tenant-config",
                        "Authorization", "Bearer blocked-config")),
                headers(
                        "x-tenant", "tenant-request",
                        "userid", "user-request",
                        "authorization", "Bearer blocked-request"),
                false);

        assertEquals("tenant-request", captured.header("X-Tenant"));
        assertEquals("user-request", captured.header("userid"));
        assertEquals("Bearer sk-test", captured.header("Authorization"));
    }

    @Test
    @DisplayName("Test headers validation blocks protected values during stream")
    void testHeadersValidation() throws Exception {
        CapturedRequest captured = invoke(
                baseClientConfig(null, headers(
                        "UserID", "user-cfg",
                        "Host", "blocked")),
                headers(
                        "UserID", "user-req",
                        "Connection", "blocked"),
                true);

        assertEquals("user-req", captured.header("UserID"));
        assertTrue(captured.header("Connection") == null
                || !"blocked".equals(captured.header("Connection")));
        assertTrue(captured.header("Host") == null
                || !"blocked".equals(captured.header("Host")));
    }

    @Test
    @DisplayName("Test react agent config propagates custom headers")
    void testReactAgentConfigPropagatesCustomHeaders() throws Exception {
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureCustomHeaders(headers(
                "Token", "token-react",
                "UserID", "user-react",
                "Connection", "blocked"));

        CapturedRequest captured = invoke(
                buildReactConfigModelClient(config),
                headers("UserID", "user-override", "X-Empty", null),
                false);

        assertEquals("token-react", captured.header("Token"));
        assertEquals("user-override", captured.header("UserID"));
        assertTrue(captured.header("Connection") == null
                || !"blocked".equals(captured.header("Connection")));
    }

    @Test
    @DisplayName("Test stream injects effective headers")
    void testStreamInjectsEffectiveHeaders() throws Exception {
        CapturedRequest captured = invoke(
                baseClientConfig(null, headers(
                        "UserID", "user-cfg",
                        "Host", "blocked")),
                headers(
                        "UserID", "user-req",
                        "Connection", "blocked"),
                true);

        assertEquals("user-req", captured.header("UserID"));
        assertEquals("Bearer sk-test", captured.header("Authorization"));
    }

    @Test
    @DisplayName("Test model fingerprint stays stable with different custom headers")
    void testModelFingerprintStaysStableWithDifferentCustomHeaders() {
        ModelClientConfig cfg1 = ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .customHeaders(headers("Token", "token-a", "UserID", "user-a"))
                .build();
        ModelClientConfig cfg2 = ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .customHeaders(headers("Token", "token-b", "UserID", "user-b"))
                .build();

        assertEquals(
                HashUtil.generateKey(cfg1.getApiKey(), cfg1.getApiBase(), cfg1.getClientProvider()),
                HashUtil.generateKey(cfg2.getApiKey(), cfg2.getApiBase(), cfg2.getClientProvider()));
    }

    private CapturedRequest invoke(ModelClientConfig clientConfig,
                                   Map<String, Object> requestHeaders,
                                   boolean stream) throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = createServer(captured, stream);
        server.start();
        try {
            Model model = new Model(
                    ModelClientConfig.builder()
                            .clientProvider(clientConfig.getClientProvider())
                            .apiKey(clientConfig.getApiKey())
                            .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                            .verifySsl(false)
                            .customHeaders(clientConfig.getCustomHeaders())
                            .build(),
                    ModelRequestConfig.builder().modelName("gpt-4o-mini").build());

            if (stream) {
                Iterator<AssistantMessageChunk> chunks = model.stream(
                        List.of(new UserMessage("hello")),
                        null, null, null, null, null, null, null, null,
                        requestHeaders == null ? null : Map.of("custom_headers", requestHeaders));
                while (chunks.hasNext()) {
                    chunks.next();
                }
            } else {
                AssistantMessage message = model.invoke(
                        List.of(new UserMessage("hello")),
                        null, null, null, null, null, null, null, null,
                        requestHeaders == null ? null : Map.of("custom_headers", requestHeaders));
                assertEquals("ok", message.getContentAsString());
            }
            return captured;
        } finally {
            server.stop(0);
        }
    }

    private static ModelClientConfig buildReactConfigModelClient(ReActAgentConfig config) {
        config.configureModelClient(
                ProviderType.OpenAI.getValue(),
                "sk-test",
                "http://127.0.0.1:1/v1",
                "gpt-4o-mini",
                false);
        return config.getModelClientConfig();
    }

    private static ModelClientConfig baseClientConfig(String apiBase, Map<String, Object> customHeaders) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey("sk-test")
                .apiBase(apiBase == null ? "http://127.0.0.1:1/v1" : apiBase)
                .verifySsl(false)
                .customHeaders(customHeaders)
                .build();
    }

    private static Map<String, Object> headers(Object... keyValues) {
        Map<String, Object> headers = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            headers.put((String) keyValues[i], keyValues[i + 1]);
        }
        return headers;
    }

    private static HttpServer createServer(CapturedRequest captured, boolean stream) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.capture(exchange);
            if (stream) {
                writeText(exchange, "text/event-stream",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                                + "data: [DONE]\n\n");
            } else {
                writeText(exchange, "application/json",
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}");
            }
        });
        return server;
    }

    private static void writeText(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static final class CapturedRequest {
        private Map<String, List<String>> headers = Map.of();
        private String body = "";

        void capture(HttpExchange exchange) throws IOException {
            headers = new LinkedHashMap<>(exchange.getRequestHeaders());
            try (InputStream inputStream = exchange.getRequestBody()) {
                body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String header(String key) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue().isEmpty() ? null : entry.getValue().getFirst();
                }
            }
            return null;
        }
    }
}
