/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.foundation.llm.model_clients.OpenAiCompatibleModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    @Test
    void clientTimeoutRemainsTheTransportTimeoutWhenCallOverrideIsNull() throws Exception {
        ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("sk-test")
                .apiBase("http://127.0.0.1:1/v1").timeout(1200).build();
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                ModelRequestConfig.builder().modelName("test-model").build(), clientConfig);

        Field httpClientField = OpenAiCompatibleModelClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        OkHttpClient httpClient = assertInstanceOf(OkHttpClient.class, httpClientField.get(client));

        assertEquals(1_200_000, httpClient.readTimeoutMillis());
        assertEquals(1_200_000, httpClient.writeTimeoutMillis());
        assertEquals(0, httpClient.callTimeoutMillis());

        Call call = new OkHttpClient().newCall(new Request.Builder().url("http://127.0.0.1:1").build());
        Method applyCallTimeout = OpenAiCompatibleModelClient.class
                .getDeclaredMethod("applyCallTimeout", Call.class, Float.class);
        applyCallTimeout.setAccessible(true);
        applyCallTimeout.invoke(null, call, null);
        assertEquals(0, call.timeout().timeoutNanos());

        applyCallTimeout.invoke(null, call, 2.5f);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(2500), call.timeout().timeoutNanos());
    }

    @Test
    void streamParsesDashScopeStyleReasoningAndContentChunks() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> writeSse(exchange, String.join("",
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"},"
                        + "\"finish_reason\":null}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":null}]}\n\n",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n",
                "data: [DONE]\n\n")));
        server.start();

        try {
            ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("sk-test")
                    .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").timeout(5).build();
            Model model = new Model(clientConfig,
                    ModelRequestConfig.builder().modelName("qwen-thinking").build());

            Iterator<AssistantMessageChunk> stream = model.stream(List.of(new UserMessage("hello")), null,
                    null, null, null, null, null, null, null, null);
            AssistantMessageChunk merged = null;
            while (stream.hasNext()) {
                AssistantMessageChunk chunk = stream.next();
                merged = merged == null ? chunk : merged.merge(chunk);
            }

            assertEquals("thinking", merged.getReasoningContent());
            assertEquals("answer", merged.getContentAsString());
            assertEquals("stop", merged.getFinishReason());
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

    private static void writeSse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
