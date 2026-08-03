/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity checks for T01179.
 *
 * <p>Mirrors Python's {@code HTTPRequestExecutable} and {@code HTTPRequestComponent} in
 * {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.</p>
 */
class T01179HTTPRequestComponentTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void invokeResolvesTemplatesAuthQueryJsonBodyAndResponseProperty() throws Exception {
        HttpServer server = startServer(exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> payload = Map.of(
                    "method", exchange.getRequestMethod(),
                    "path", exchange.getRequestURI().getPath(),
                    "query", exchange.getRequestURI().getRawQuery(),
                    "auth", exchange.getRequestHeaders().getFirst("Authorization"),
                    "body", JSON.readValue(requestBody, new TypeReference<Map<String, Object>>() {
                    })
            );
            sendJson(exchange, 200, Map.of("payload", payload));
        });
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo/{{id}}";
            HttpRequestParamConfig params = HttpRequestParamConfig.builder()
                    .url(url)
                    .method("post")
                    .queryParameters(Map.of("q", "{{id}}"))
                    .body(HttpRequestBodyConfig.builder()
                            .contentType(HttpContentType.JSON)
                            .jsonData("{\"name\":\"{{id}}\"}")
                            .build())
                    .authentication(HttpAuthConfig.builder()
                            .type(HttpAuthType.BEARER)
                            .token("token-123")
                            .build())
                    .responseHandling(HttpResponseHandlingConfig.builder()
                            .responseFormat(HttpResponseFormat.JSON)
                            .responseDataProperty("payload")
                            .build())
                    .build();

            Map<?, ?> output = invoke(params, Map.of("id", "42"));
            Map<?, ?> body = assertInstanceOf(Map.class, output.get("body"));
            Map<?, ?> echoedBody = assertInstanceOf(Map.class, body.get("body"));

            assertEquals(200, output.get("statusCode"));
            assertEquals(Boolean.TRUE, output.get("ok"));
            assertEquals("POST", body.get("method"));
            assertEquals("/echo/42", body.get("path"));
            assertEquals("q=42", body.get("query"));
            assertEquals("Bearer token-123", body.get("auth"));
            assertEquals("42", echoedBody.get("name"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retryOnConfiguredStatusThenReturnsSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            int current = calls.incrementAndGet();
            if (current == 1) {
                sendJson(exchange, 500, Map.of("error", "retry"));
            } else {
                sendJson(exchange, 200, Map.of("ok", true));
            }
        });
        try {
            HttpRequestParamConfig params = HttpRequestParamConfig.builder()
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/retry")
                    .method("GET")
                    .retryConfig(HttpRetryConfig.builder()
                            .enabled(true)
                            .maxRetries(1)
                            .retryOnStatusCodes(List.of(500))
                            .retryDelay(1)
                            .backoffType("fixed")
                            .build())
                    .responseHandling(HttpResponseHandlingConfig.builder()
                            .responseFormat(HttpResponseFormat.JSON)
                            .build())
                    .build();

            Map<?, ?> output = invoke(params, Map.of());

            assertEquals(2, calls.get());
            assertEquals(200, output.get("statusCode"));
            assertEquals(Boolean.TRUE, output.get("ok"));
            assertEquals(Boolean.TRUE, assertInstanceOf(Map.class, output.get("body")).get("ok"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void responseModeOnSuccessSuppressesErrorOutput() throws Exception {
        HttpServer server = startServer(exchange -> sendJson(exchange, 500, Map.of("error", "boom")));
        try {
            HttpRequestParamConfig params = HttpRequestParamConfig.builder()
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/error")
                    .method("GET")
                    .responseHandling(HttpResponseHandlingConfig.builder()
                            .responseMode("on-success")
                            .responseFormat(HttpResponseFormat.JSON)
                            .build())
                    .build();

            Map<?, ?> output = invoke(params, Map.of());

            assertTrue(output.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void apiKeyCanBeInjectedIntoQueryString() throws Exception {
        HttpServer server = startServer(exchange -> sendJson(exchange, 200, Map.of(
                "query", exchange.getRequestURI().getRawQuery()
        )));
        try {
            HttpRequestParamConfig params = HttpRequestParamConfig.builder()
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/api-key")
                    .method("GET")
                    .authentication(HttpAuthConfig.builder()
                            .type(HttpAuthType.API_KEY)
                            .apiKey("secret")
                            .inLocation("query")
                            .name("api_key")
                            .build())
                    .responseHandling(HttpResponseHandlingConfig.builder()
                            .responseFormat(HttpResponseFormat.JSON)
                            .build())
                    .build();

            Map<?, ?> output = invoke(params, Map.of());

            assertEquals("api_key=secret", assertInstanceOf(Map.class, output.get("body")).get("query"));
        } finally {
            server.stop(0);
        }
    }

    private static Map<?, ?> invoke(HttpRequestParamConfig requestParams, Map<String, Object> inputs) {
        HttpComponentConfig config = new HttpComponentConfig();
        config.setRequestParams(requestParams);
        Object output = new HTTPRequestComponent(config).getExecutable().invoke(inputs, null, null);
        return assertInstanceOf(Map.class, output);
    }

    private static HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                byte[] body = exception.getMessage().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static void sendJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] response = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
