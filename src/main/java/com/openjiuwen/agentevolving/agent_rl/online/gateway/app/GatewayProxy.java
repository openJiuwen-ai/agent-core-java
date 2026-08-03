/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;
import com.openjiuwen.core.common.VirtualThreadSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway CLI and embedded server entrypoints.
 *
 * <p>Mirrors Python's module-level factory and CLI helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/proxy.py}.</p>
 */
public final class GatewayProxy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private GatewayProxy() {
    }

    public static GatewayServer createApp() {
        GatewayConfig config = GatewayBootstrap.buildConfigFromEnv();
        return GatewayBootstrap.buildAppFromConfig(config);
    }

    public static void main(String[] args) throws Exception {
        GatewayConfig config = buildCliConfig(args);
        HostedGatewayServer server = start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Thread.currentThread().join();
    }

    static GatewayConfig buildCliConfig(String[] args) {
        Map<String, String> values = parseArgs(args);
        GatewayConfig config = new GatewayConfig();
        config.setHost(values.getOrDefault("--host", "127.0.0.1"));
        config.setPort(Integer.parseInt(required(values, "--port")));
        config.setLlmUrl(values.getOrDefault("--llm-url", "http://127.0.0.1:18000"));
        config.setJudgeUrl(values.getOrDefault("--judge-url", config.getLlmUrl()));
        config.setModelId(values.getOrDefault("--model-id", ""));
        config.setJudgeModel(values.getOrDefault("--judge-model", ""));
        config.setRecordDir(values.getOrDefault("--record-dir", "records"));
        config.setLoraRepoRoot(values.getOrDefault("--lora-repo-root", ""));
        config.setLogLevel(values.getOrDefault("--log-level", "INFO"));
        return config;
    }

    static HostedGatewayServer start(GatewayConfig config) throws IOException {
        GatewayServer app = GatewayBootstrap.buildAppFromConfig(config);
        return start(config.getHost(), config.getPort(), app);
    }

    static HostedGatewayServer start(String host, int port, GatewayServer app) throws IOException {
        HostedGatewayServer server = new HostedGatewayServer(host, port, app);
        server.start();
        return server;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + arg);
            }
            values.put(arg, args[++index]);
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    static final class HostedGatewayServer implements AutoCloseable {
        private final GatewayServer app;
        private final HttpServer server;

        HostedGatewayServer(String host, int port, GatewayServer app) throws IOException {
            this.app = app;
            this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
            this.server.createContext("/health", this::handleHealth);
            this.server.createContext("/v1/gateway/stats", this::handleStats);
            this.server.createContext("/v1/gateway/upload/batch", this::handleUploadBatch);
            this.server.createContext("/v1/chat/completions", this::handleChatCompletions);
            this.server.createContext("/", this::handleProxyOther);
            this.server.setExecutor(VirtualThreadSupport.newThreadPerTaskExecutor());
        }

        void start() {
            server.start();
        }

        int getPort() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            try {
                app.close();
            } catch (Exception ignored) {
                // Keep shutdown best-effort like Python's process exit behavior.
            }
        }

        private void handleHealth(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("detail", "method not allowed"));
                return;
            }
            sendJson(exchange, 200, app.health());
        }

        private void handleStats(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("detail", "method not allowed"));
                return;
            }
            try {
                sendJson(exchange, 200, app.gatewayStats(header(exchange, "Authorization")));
            } catch (GatewayHttpException exception) {
                sendJson(exchange, exception.getStatusCode(), Map.of("detail", exception.getDetail()));
            }
        }

        private void handleUploadBatch(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("detail", "method not allowed"));
                return;
            }
            try {
                Map<String, Object> payload = readJsonMap(exchange);
                sendJson(exchange, 200, app.createUploadBatch(payload, header(exchange, "Authorization")));
            } catch (GatewayHttpException exception) {
                sendJson(exchange, exception.getStatusCode(), Map.of("detail", exception.getDetail()));
            } catch (Exception exception) {
                sendJson(exchange, 400, Map.of("detail", "invalid json: " + exception.getMessage()));
            }
        }

        private void handleChatCompletions(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("detail", "method not allowed"));
                return;
            }
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            try {
                GatewayServer.ChatCompletionResult result = app.chatCompletions(
                        flattenHeaders(exchange.getRequestHeaders()),
                        requestBody,
                        header(exchange, "Authorization")
                );
                if (result.stream()) {
                    sendBytes(
                            exchange,
                            200,
                            String.join("", result.eventStream()).getBytes(StandardCharsets.UTF_8),
                            Map.of("Content-Type", result.mediaType())
                    );
                    return;
                }
                sendJson(exchange, 200, result.jsonBody(), result.mediaType());
            } catch (GatewayHttpException exception) {
                sendJson(exchange, exception.getStatusCode(), Map.of("detail", exception.getDetail()));
            } catch (Exception exception) {
                sendJson(exchange, 400, Map.of("detail", "invalid json: " + exception.getMessage()));
            }
        }

        private void handleProxyOther(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/health".equals(path)
                    || "/v1/gateway/stats".equals(path)
                    || "/v1/gateway/upload/batch".equals(path)
                    || "/v1/chat/completions".equals(path)) {
                sendJson(exchange, 404, Map.of("detail", "not found"));
                return;
            }
            try {
                GatewayServer.ProxyForwardResult response = app.proxyOther(
                        path.startsWith("/") ? path.substring(1) : path,
                        exchange.getRequestMethod(),
                        queryParams(exchange.getRequestURI()),
                        flattenHeaders(exchange.getRequestHeaders()),
                        exchange.getRequestBody().readAllBytes(),
                        header(exchange, "Authorization")
                );
                Map<String, String> headers = new LinkedHashMap<>(response.headers());
                if (response.mediaType() != null && !response.mediaType().isBlank()) {
                    headers.putIfAbsent("Content-Type", response.mediaType());
                }
                sendBytes(exchange, response.statusCode(), response.content(), headers);
            } catch (GatewayHttpException exception) {
                sendJson(exchange, exception.getStatusCode(), Map.of("detail", exception.getDetail()));
            }
        }

        private static Map<String, Object> readJsonMap(HttpExchange exchange) throws IOException {
            byte[] payload = exchange.getRequestBody().readAllBytes();
            return payload.length == 0 ? new LinkedHashMap<>() : OBJECT_MAPPER.readValue(payload, MAP_TYPE);
        }
    }

    private static Map<String, String> flattenHeaders(Headers headers) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                flattened.put(name, values.get(0));
            }
        });
        return flattened;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static Map<String, Object> queryParams(URI uri) {
        Map<String, Object> params = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        sendJson(exchange, statusCode, payload, "application/json");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload, String mediaType)
            throws IOException {
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(payload);
        sendBytes(exchange, statusCode, body, Map.of("Content-Type", mediaType));
    }

    private static void sendBytes(HttpExchange exchange, int statusCode, byte[] body, Map<String, String> headers)
            throws IOException {
        headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
