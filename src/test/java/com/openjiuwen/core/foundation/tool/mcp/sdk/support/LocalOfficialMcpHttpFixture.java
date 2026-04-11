/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.sdk.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * Local HTTP fixture for official MCP transport tests.
 */
public final class LocalOfficialMcpHttpFixture implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpServer server;
    private final Mode mode;

    private LocalOfficialMcpHttpFixture(HttpServer server, Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    public static LocalOfficialMcpHttpFixture start(Mode mode) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        LocalOfficialMcpHttpFixture fixture = new LocalOfficialMcpHttpFixture(server, mode);
        server.createContext("/mcp", fixture::handleRpc);
        server.createContext("/sse", fixture::handleRpc);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return fixture;
    }

    public String streamableHttpUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    public String sseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/sse";
    }

    private void handleRpc(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleSse(exchange);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        Map<String, Object> request;
        try (InputStream inputStream = exchange.getRequestBody()) {
            request = JSON.readValue(inputStream, MAP_TYPE);
        }
        String method = String.valueOf(request.getOrDefault("method", ""));
        Object requestId = request.get("id");

        if (mode == Mode.TIMEOUT_INITIALIZE && "initialize".equals(method)) {
            sleepQuietly(600);
        }
        if (mode == Mode.TIMEOUT_LIST_TOOLS && "tools/list".equals(method)) {
            sleepQuietly(600);
        }
        Map<String, Object> params = castMap(request.get("params"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", requestId);
        if (mode == Mode.FAIL_INITIALIZE && "initialize".equals(method)) {
            response.put("error", Map.of("code", -32001, "message", "initialize failed from fixture"));
            respondJson(exchange, 200, response);
            return;
        }
        if (mode == Mode.FAIL_LIST_TOOLS && "tools/list".equals(method)) {
            response.put("error", Map.of("code", -32002, "message", "tools/list failed from fixture"));
            respondJson(exchange, 200, response);
            return;
        }
        if ("notifications/initialized".equals(method)) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        if ("tools/call".equals(method)) {
            handleToolCall(exchange, response, params);
            return;
        }

        response.put("result", switch (method) {
            case "initialize" -> Map.of(
                    "protocolVersion", "2024-11-05",
                    "serverInfo", Map.of("name", "LocalOfficialMcpHttpFixture", "version", "1.0.0"),
                    "capabilities", Map.of("tools", Map.of())
            );
            case "tools/list" -> Map.of(
                    "tools", List.of(Map.of(
                            "name", "fixture_http_tool",
                            "description", "fixture tool from tools/list",
                            "inputSchema", Map.of(
                                    "type", "object",
                                    "properties", Map.of("city", Map.of("type", "string")),
                                    "required", List.of("city")
                            )
                    ))
            );
            default -> throw new IllegalArgumentException("Unsupported MCP method: " + method);
        });
        respondJson(exchange, 200, response);
    }

    private void handleToolCall(HttpExchange exchange, Map<String, Object> response, Map<String, Object> params)
            throws IOException {
        String toolName = String.valueOf(params.getOrDefault("name", ""));
        if (mode == Mode.CALL_UNKNOWN_TOOL || !Objects.equals(toolName, "fixture_http_tool")) {
            response.put("error", Map.of("code", -32003, "message", "kind=tool_missing tool not found: " + toolName));
            respondJson(exchange, 200, response);
            return;
        }

        if (mode == Mode.SUCCESS_STRUCTURED_RESULT) {
            response.put("result", Map.of(
                    "content", List.of(
                            Map.of("type", "text", "text", "weather for 北京"),
                            Map.of("type", "text", "text", "temperature is 22℃")
                    ),
                    "structuredContent", Map.of("location", "北京", "temperature", "22℃"),
                    "isError", false
            ));
            respondJson(exchange, 200, response);
            return;
        }

        response.put("result", Map.of(
                "content", List.of(Map.of("type", "text", "text", "fixture ok")),
                "structuredContent", Map.of(),
                "isError", false
        ));
        respondJson(exchange, 200, response);
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().endsWith("/sse")) {
            respondJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(": connected\n\n".getBytes());
            outputStream.flush();
            sleepQuietly(1000);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respondJson(HttpExchange exchange, int statusCode, Map<String, Object> body) throws IOException {
        byte[] payload = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public enum Mode {
        SUCCESS,
        SUCCESS_TEXT_RESULT,
        SUCCESS_STRUCTURED_RESULT,
        CALL_UNKNOWN_TOOL,
        FAIL_INITIALIZE,
        FAIL_LIST_TOOLS,
        TIMEOUT_INITIALIZE,
        TIMEOUT_LIST_TOOLS
    }
}
