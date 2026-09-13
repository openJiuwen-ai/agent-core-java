/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class OpenAiCompatibleModelClientTest {
    @Test
    void invokeAppliesConfiguredHeadersAndAllowsAuthorizationOverride() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> traceHeader = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            traceHeader.set(exchange.getRequestHeaders().getFirst("X-Trace"));
            writeJson(exchange, "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
        });
        server.start();

        try {
            ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("sk-test")
                    .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .headers(Map.of("Authorization", "Basic custom", "X-Trace", "trace-123")).build();
            ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("test-model").build();

            Model model = new Model(clientConfig, requestConfig);
            AssistantMessage response =
                model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null, null, null);

            assertEquals("ok", response.getContentAsString());
            assertEquals("Basic custom", authorization.get());
            assertEquals("trace-123", traceHeader.get());
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
}
