/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for model client without custom headers.
 * <p>
 * Mirrors Python's {@code test_model_no_custom_headers.py} from
 * {@code tests/unit_tests/core/foundation/llm/test_model_no_custom_headers.py}.
 */
class TestModelNoCustomHeaders {

    @Test
    @Tag("level0")
    void testModelClientConfigClassExists() {
        assertNotNull(ModelClientConfig.class);
    }

    @Test
    @Tag("level1")
    void testValidConfigWithoutCustomHeadersHasNoConfiguredHeaders() {
        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .verifySsl(false)
                .build();

        assertEquals("sk-test", config.getApiKey());
        assertEquals("https://api.openai.com/v1", config.getApiBase());
        assertEquals(ProviderType.OpenAI.getValue(), config.getClientProvider());
        assertEquals(0, config.getHeaders().size());
    }

    @ParameterizedTest
    @EnumSource(ModelBuildMode.class)
    @Tag("level2")
    void testInvokeWithoutCustomHeadersDoesNotSendExtraHeaders(ModelBuildMode mode) throws Exception {
        CapturedRequest captured = invokeAndCapture(mode);

        assertEquals("Bearer sk-test", captured.authorization.get());
        assertNull(captured.token.get());
        assertNull(captured.userId.get());
        assertFalse(captured.body.get().contains("extra_headers"));
    }

    @ParameterizedTest
    @EnumSource(ModelBuildMode.class)
    @Tag("level2")
    void testStreamWithoutCustomHeadersDoesNotSendExtraHeaders(ModelBuildMode mode) throws Exception {
        CapturedRequest captured = streamAndCapture(mode);

        assertEquals("Bearer sk-test", captured.authorization.get());
        assertNull(captured.token.get());
        assertNull(captured.userId.get());
        assertFalse(captured.body.get().contains("extra_headers"));
    }

    @Test
    @Tag("level3")
    void testBuilderPattern() {
        assertNotNull(ModelClientConfig.builder());
    }

    private CapturedRequest invokeAndCapture(ModelBuildMode mode) throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = createServer(captured, false);
        server.start();
        try {
            Model model = buildModel(mode, server);
            AssistantMessage response = model.invoke(
                    List.of(new UserMessage("hello")),
                    null, null, null, null, null, null, null, null, null);
            assertEquals("ok", response.getContentAsString());
            return captured;
        } finally {
            server.stop(0);
        }
    }

    private CapturedRequest streamAndCapture(ModelBuildMode mode) throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = createServer(captured, true);
        server.start();
        try {
            Model model = buildModel(mode, server);
            Iterator<AssistantMessageChunk> chunks = model.stream(
                    List.of(new UserMessage("hello")),
                    null, null, null, null, null, null, null, null, null);
            while (chunks.hasNext()) {
                chunks.next();
            }
            return captured;
        } finally {
            server.stop(0);
        }
    }

    private HttpServer createServer(CapturedRequest captured, boolean stream) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            captured.token.set(exchange.getRequestHeaders().getFirst("Token"));
            captured.userId.set(exchange.getRequestHeaders().getFirst("UserID"));
            captured.body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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

    private Model buildModel(ModelBuildMode mode, HttpServer server) {
        String apiBase = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        if (mode == ModelBuildMode.REACT_AGENT_CONFIG) {
            ReActAgentConfig config = new ReActAgentConfig();
            config.configureModelClient(
                    ProviderType.OpenAI.getValue(),
                    "sk-test",
                    apiBase,
                    "gpt-4o-mini",
                    false);
            return new Model(config.getModelClientConfig(), config.getModelConfigObj());
        }

        return new Model(
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OpenAI.getValue())
                        .apiKey("sk-test")
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .build(),
                ModelRequestConfig.builder()
                        .modelName("gpt-4o-mini")
                        .build());
    }

    private static void writeText(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private enum ModelBuildMode {
        DIRECT_MODEL,
        REACT_AGENT_CONFIG
    }

    private static final class CapturedRequest {
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> token = new AtomicReference<>();
        private final AtomicReference<String> userId = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>("");
    }
}
