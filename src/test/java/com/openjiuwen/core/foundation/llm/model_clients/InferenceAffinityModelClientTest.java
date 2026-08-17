/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.KvCacheReleaseRequest;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for {@link InferenceAffinityModelClient#release} and
 * {@link InferenceAffinityModelClient#supportsKvCacheRelease}.
 * <p>
 * Uses a JDK built-in {@link HttpServer} on 127.0.0.1 with an ephemeral port
 * to mock the vLLM {@code /release_kv_cache} endpoint, mirroring the
 * {@code OpenAiCompatibleModelClientTest} pattern.
 */
class InferenceAffinityModelClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("supportsKvCacheRelease returns true")
    void supportsKvCacheRelease_returnsTrue() {
        InferenceAffinityModelClient client = newClient("http://127.0.0.1:1");
        assertThat(client.supportsKvCacheRelease()).isTrue();
    }

    @Test
    @DisplayName("release returns true and POSTs expected body on HTTP 200")
    void release_whenHttp200_returnsTrueAndSendsExpectedBody() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        HttpServer server = newServer("/release_kv_cache", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(readBody(exchange));
            writeJson(exchange, "{\"cache_salt\":\"sess-1\",\"block_released\":3}");
        });

        try {
            server.start();
            InferenceAffinityModelClient client =
                newClient("http://127.0.0.1:" + server.getAddress().getPort());

            boolean result = client.release(new KvCacheReleaseRequest("sess-1",
                List.of(new UserMessage("hello"), new UserMessage("world")), 1,
                null, null, null));

            assertThat(result).as("release should return true on HTTP 2xx").isTrue();
            assertThat(capturedPath.get()).isEqualTo("/release_kv_cache");

            Map<String, Object> body = MAPPER.readValue(capturedBody.get(), Map.class);
            assertThat(body).containsEntry("model", "test-model");
            assertThat(body).containsEntry("cache_salt", "sess-1");
            assertThat(body).containsEntry("cache_sharing", true);
            assertThat(body).containsEntry("messages_released_index", 1);
            assertThat(body).containsKey("messages");
            assertThat(body).doesNotContainKey("tools");
            assertThat(body).doesNotContainKey("tools_released_index");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("release uses explicit model name when provided")
    void release_whenModelProvided_usesExplicitModel() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = newServer("/release_kv_cache", exchange -> {
            capturedBody.set(readBody(exchange));
            writeJson(exchange, "{\"block_released\":1}");
        });

        try {
            server.start();
            InferenceAffinityModelClient client =
                newClient("http://127.0.0.1:" + server.getAddress().getPort());

            client.release(new KvCacheReleaseRequest("sess-2", List.of(new UserMessage("hi")), 0,
                null, null, "explicit-model-name"));

            Map<String, Object> body = MAPPER.readValue(capturedBody.get(), Map.class);
            assertThat(body).containsEntry("model", "explicit-model-name");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("release includes tools and tools_released_index when provided")
    void release_whenToolsProvided_includesToolFields() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = newServer("/release_kv_cache", exchange -> {
            capturedBody.set(readBody(exchange));
            writeJson(exchange, "{\"block_released\":2}");
        });

        try {
            server.start();
            InferenceAffinityModelClient client =
                newClient("http://127.0.0.1:" + server.getAddress().getPort());

            Map<String, Object> toolParams = new java.util.LinkedHashMap<>();
            toolParams.put("type", "object");
            Map<String, Object> tool = new java.util.LinkedHashMap<>();
            tool.put("name", "weather");
            tool.put("description", "get weather");
            tool.put("parameters", toolParams);

            client.release(new KvCacheReleaseRequest("sess-3", List.of(new UserMessage("hi")), 0,
                List.of(tool), 2, null));

            Map<String, Object> body = MAPPER.readValue(capturedBody.get(), Map.class);
            assertThat(body).containsEntry("tools_released_index", 2);
            assertThat(body).containsKey("tools");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("release returns false on HTTP 404 (endpoint not implemented)")
    void release_whenHttp404_returnsFalseWithoutThrowing() throws Exception {
        HttpServer server = newServer("/release_kv_cache",
            exchange -> writeText(exchange, 404, "Not Found"));

        try {
            server.start();
            InferenceAffinityModelClient client =
                newClient("http://127.0.0.1:" + server.getAddress().getPort());

            boolean result = client.release(new KvCacheReleaseRequest("sess-4",
                List.of(new UserMessage("hi")), 0, null, null, null));

            assertThat(result).as("release should return false on HTTP 404").isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("release returns false on HTTP 500")
    void release_whenHttp500_returnsFalseWithoutThrowing() throws Exception {
        HttpServer server = newServer("/release_kv_cache",
            exchange -> writeText(exchange, 500, "Internal Server Error"));

        try {
            server.start();
            InferenceAffinityModelClient client =
                newClient("http://127.0.0.1:" + server.getAddress().getPort());

            boolean result = client.release(new KvCacheReleaseRequest("sess-5",
                List.of(new UserMessage("hi")), 0, null, null, null));

            assertThat(result).as("release should return false on HTTP 500").isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("release propagates IOException when connection refused")
    void release_whenConnectionRefused_throwsException() {
        // Use a port that is almost certainly not listening — connection refused
        InferenceAffinityModelClient client = newClient("http://127.0.0.1:1");

        assertThatThrownBy(() -> client.release(new KvCacheReleaseRequest("sess-6",
            List.of(new UserMessage("hi")), 0, null, null, null)))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("release sends POST to {apiBase}/release_kv_cache with trailing slash stripped")
    void release_stripsTrailingSlashFromApiBase() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        HttpServer server = newServer("/release_kv_cache",
            exchange -> {
                capturedPath.set(exchange.getRequestURI().getPath());
                writeJson(exchange, "{\"block_released\":0}");
            });

        try {
            server.start();
            // apiBase with trailing slash — should be stripped
            InferenceAffinityModelClient client = newClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/");

            boolean result = client.release(new KvCacheReleaseRequest("sess-7",
                List.of(new UserMessage("hi")), 0, null, null, null));

            assertThat(result).isTrue();
            assertThat(capturedPath.get()).isEqualTo("/release_kv_cache");
        } finally {
            server.stop(0);
        }
    }

    private static InferenceAffinityModelClient newClient(String apiBase) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
            .clientProvider("InferenceAffinity")
            .apiKey("sk-test")
            .apiBase(apiBase)
            .timeout(5.0)
            .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
            .modelName("test-model")
            .build();
        return new InferenceAffinityModelClient(requestConfig, clientConfig);
    }

    private static HttpServer newServer(String contextPath, HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(contextPath, exchange -> {
                try {
                    handler.handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start test HttpServer", e);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void writeText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
