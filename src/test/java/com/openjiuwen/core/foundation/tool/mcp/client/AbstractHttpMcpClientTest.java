/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Focused unit tests for HTTP MCP client handshake, JSON-RPC id checks, and multi-block tool results.
 */
class AbstractHttpMcpClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("callTool joins multiple text content blocks with newlines")
    void callToolJoinsMultipleTextBlocks() throws Exception {
        server = startServer(exchange -> {
            Map<String, Object> request = readJson(exchange);
            Object id = request.get("id");
            String method = String.valueOf(request.get("method"));
            if ("initialize".equals(method)) {
                writeJson(exchange, rpcResult(id, Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of(),
                        "serverInfo", Map.of("name", "mock", "version", "1.0"))));
                return;
            }
            if ("notifications/initialized".equals(method)) {
                writeEmpty(exchange);
                return;
            }
            if ("tools/call".equals(method)) {
                writeJson(exchange, rpcResult(id, Map.of("content",
                        List.of(Map.of("type", "text", "text", "first"), Map.of("type", "text", "text", "second")))));
                return;
            }
            writeJson(exchange, Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("message", "unexpected")));
        });

        StreamableHttpClient client = newClient();
        assertTrue(client.connect(0, 5f));
        Object result = client.callTool("echo", Map.of(), 5f);
        assertEquals("first\nsecond", result);
    }

    @Test
    @DisplayName("connect sends notifications/initialized without id after initialize")
    void connectSendsInitializedNotification() throws Exception {
        List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();
        server = startServer(exchange -> {
            Map<String, Object> request = readJson(exchange);
            requests.add(request);
            String method = String.valueOf(request.get("method"));
            if ("initialize".equals(method)) {
                writeJson(exchange, rpcResult(request.get("id"), Map.of("protocolVersion", "2024-11-05",
                        "capabilities", Map.of(), "serverInfo", Map.of("name", "mock", "version", "1.0"))));
                return;
            }
            if ("notifications/initialized".equals(method)) {
                writeEmpty(exchange);
                return;
            }
            writeJson(exchange, Map.of("jsonrpc", "2.0", "id", request.get("id"), "error",
                    Map.of("message", "unexpected")));
        });

        StreamableHttpClient client = newClient();
        assertTrue(client.connect(0, 5f));

        assertEquals(2, requests.size());
        assertEquals("initialize", requests.get(0).get("method"));
        assertTrue(requests.get(0).containsKey("id"));
        assertEquals("notifications/initialized", requests.get(1).get("method"));
        assertTrue(!requests.get(1).containsKey("id"));
    }

    @Test
    @DisplayName("callRpc throws when response id does not match request id")
    void callRpcRejectsMismatchedResponseId() throws Exception {
        server = startServer(exchange -> {
            Map<String, Object> request = readJson(exchange);
            String method = String.valueOf(request.get("method"));
            if ("initialize".equals(method)) {
                writeJson(exchange, rpcResult(request.get("id"), Map.of("protocolVersion", "2024-11-05",
                        "capabilities", Map.of(), "serverInfo", Map.of("name", "mock", "version", "1.0"))));
                return;
            }
            if ("notifications/initialized".equals(method)) {
                writeEmpty(exchange);
                return;
            }
            writeJson(exchange, rpcResult(9999L, Map.of("tools", List.of())));
        });

        StreamableHttpClient client = newClient();
        assertTrue(client.connect(0, 5f));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.listTools(5f));
        assertTrue(ex.getMessage().contains("JSON-RPC response id mismatch"));
    }

    @Test
    @DisplayName("HttpClient uses connectTimeoutSeconds from server config")
    void httpClientAppliesConnectTimeoutSeconds() {
        McpServerConfig config = McpServerConfig.builder().serverName("timeout-server")
                .serverPath("http://127.0.0.1:9/mcp").clientType("streamable-http").connectTimeoutSeconds(7.0).build();
        StreamableHttpClient client = new StreamableHttpClient(config);
        Duration timeout = client.httpClient.connectTimeout().orElseThrow();
        assertEquals(Duration.ofSeconds(7), timeout);
        assertInstanceOf(HttpClient.class, client.httpClient);
    }

    private StreamableHttpClient newClient() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        McpServerConfig config = McpServerConfig.builder().serverName("mock-server").serverPath(url)
                .clientType("streamable-http").build();
        return new StreamableHttpClient(config);
    }

    private HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/mcp", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                writeJson(exchange, Map.of("jsonrpc", "2.0", "error", Map.of("message", e.getMessage())));
            }
        });
        httpServer.start();
        return httpServer;
    }

    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return MAPPER.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private static Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("result", result);
        return payload;
    }

    private static void writeJson(HttpExchange exchange, Map<String, Object> payload) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static void writeEmpty(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
