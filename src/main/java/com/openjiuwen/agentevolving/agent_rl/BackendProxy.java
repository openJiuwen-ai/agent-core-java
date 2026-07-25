/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.openjiuwen.core.common.VirtualThreadSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reverse proxy running in a daemon HTTP server.
 *
 * <p>Mirrors Python's {@code BackendProxy} in
 * {@code openjiuwen/agent_evolving/agent_rl/proxy.py}.</p>
 */
public class BackendProxy implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> EXCLUDED_REQUEST_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "upgrade"
    );
    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = Set.of(
            "content-encoding",
            "content-length",
            "transfer-encoding",
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "upgrade"
    );

    private final double llmTimeoutSeconds;
    private final String modelName;
    private final String host;
    private final CopyOnWriteArrayList<String> backendServers = new CopyOnWriteArrayList<>();
    private final HttpClient client;
    private HttpServer server;
    private int port;
    private volatile boolean running;

    public BackendProxy() {
        this(30_000, "agentrl");
    }

    public BackendProxy(double llmTimeoutSeconds, String modelName) {
        this.llmTimeoutSeconds = llmTimeoutSeconds <= 0 ? 30_000 : llmTimeoutSeconds;
        this.modelName = modelName == null || modelName.isBlank() ? "agentrl" : modelName;
        this.host = "127.0.0.1";
        this.port = 0;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public int getPort() {
        return port;
    }

    public int port() {
        return getPort();
    }

    public String getUrl() {
        return "http://" + host + ":" + port;
    }

    public String url() {
        return getUrl();
    }

    public boolean isRunning() {
        return running;
    }

    public List<String> getBackendServers() {
        return List.copyOf(backendServers);
    }

    public List<String> backendServers() {
        return getBackendServers();
    }

    public void updateBackendServers(Object servers) {
        backendServers.clear();
        backendServers.addAll(normalizeServers(servers));
    }

    public CompletableFuture<Void> start() {
        startSync();
        return CompletableFuture.completedFuture(null);
    }

    public synchronized void startSync() {
        if (running) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            port = server.getAddress().getPort();
            server.createContext("/health", this::handleHealth);
            server.createContext("/proxy/backends", this::handleBackendUpdate);
            server.createContext("/v1/models", this::handleModels);
            server.createContext("/v1", this::handleProxy);
            server.setExecutor(VirtualThreadSupport.newThreadPerTaskExecutor("rl-backend-proxy"));
            server.start();
            running = true;
            waitForServerReady(30);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to start backend proxy", exception);
        }
    }

    public CompletableFuture<Void> stop() {
        stopSync();
        return CompletableFuture.completedFuture(null);
    }

    public synchronized void stopSync() {
        if (server != null) {
            server.stop(0);
        }
        server = null;
        running = false;
    }

    @Override
    public void close() {
        stopSync();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        sendJson(exchange, 200, Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis() / 1000.0
        ));
    }

    private void handleBackendUpdate(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            Map<String, Object> payload = readJsonMap(exchange.getRequestBody().readAllBytes());
            List<String> servers = normalizeServers(payload.get("servers"));
            if (servers.isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "Field 'servers' is required."));
                return;
            }
            updateBackendServers(servers);
            sendJson(exchange, 200, Map.of("status", "ok", "servers", getBackendServers()));
        } catch (RuntimeException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        }
    }

    private void handleModels(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        if (!backendServers.isEmpty()) {
            try {
                HttpResponse<byte[]> response = forward(exchange, "models", false);
                if (response.statusCode() == 200) {
                    relayResponse(exchange, response);
                    return;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the same local fallback response as Python.
            }
        }
        sendJson(exchange, 200, Map.of(
                "data", List.of(Map.of("id", modelName, "object", "model"))
        ));
    }

    private void handleProxy(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        if (!rawPath.startsWith("/v1/")) {
            sendJson(exchange, 404, Map.of("error", "not_found"));
            return;
        }
        if (backendServers.isEmpty()) {
            sendJson(exchange, 503, Map.of("error", "No backend LLM servers available."));
            return;
        }
        String path = rawPath.substring("/v1/".length());
        String targetServer = selectBackend();
        try {
            relayResponse(exchange, forward(exchange, path, true, targetServer));
        } catch (RuntimeException exception) {
            String targetUrl = targetBaseUrl(targetServer) + "/v1/" + path;
            sendJson(exchange, 500, Map.of(
                    "error", "proxy_request_failed",
                    "detail", exception.getMessage(),
                    "target_url", targetUrl
            ));
        }
    }

    private HttpResponse<byte[]> forward(HttpExchange exchange, String path, boolean rewriteJsonBody) {
        return forward(exchange, path, rewriteJsonBody, selectBackend());
    }

    private HttpResponse<byte[]> forward(
            HttpExchange exchange,
            String path,
            boolean rewriteJsonBody,
            String backend) {
        try {
            URI sourceUri = exchange.getRequestURI();
            String targetUrl = targetBaseUrl(backend) + "/v1/" + encodePath(path);
            if (sourceUri.getRawQuery() != null && !sourceUri.getRawQuery().isBlank()) {
                targetUrl += "?" + sourceUri.getRawQuery();
            }

            byte[] body = exchange.getRequestBody().readAllBytes();
            if (rewriteJsonBody) {
                body = rewriteRequestBody(body);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(Math.max(1, Math.round(llmTimeoutSeconds * 1000))));
            copyRequestHeaders(exchange.getRequestHeaders(), builder);
            builder.method(exchange.getRequestMethod(), bodyPublisher(exchange.getRequestMethod(), body));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("proxy request interrupted", exception);
        }
    }

    private byte[] rewriteRequestBody(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return raw == null ? new byte[0] : raw;
        }
        try {
            Map<String, Object> payload = readJsonMap(raw);
            payload.putIfAbsent("model", modelName);
            payload.put("stream", false);
            return JSON.writeValueAsBytes(payload);
        } catch (RuntimeException | JsonProcessingException ignored) {
            return raw;
        }
    }

    private void waitForServerReady(int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(getUrl() + "/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException exception) {
                // Keep retrying like Python's connection-error loop.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw proxyStartFailed();
    }

    private BaseError proxyStartFailed() {
        return ErrorHelper.buildError(
                StatusCode.AGENT_RL_PROXY_SERVER_START_FAILED,
                "host", host,
                "port", String.valueOf(port)
        );
    }

    private String selectBackend() {
        if (backendServers.isEmpty()) {
            throw new IllegalStateException("No backend LLM servers available.");
        }
        return backendServers.get(ThreadLocalRandom.current().nextInt(backendServers.size()));
    }

    private static String targetBaseUrl(String backend) {
        String trimmed = backend.trim();
        String withScheme = trimmed.startsWith("http://") || trimmed.startsWith("https://")
                ? trimmed
                : "http://" + trimmed;
        while (withScheme.endsWith("/")) {
            withScheme = withScheme.substring(0, withScheme.length() - 1);
        }
        return withScheme;
    }

    private static List<String> normalizeServers(Object servers) {
        if (servers == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (servers instanceof String raw) {
            for (String part : raw.split(",")) {
                addServer(normalized, part);
            }
            return normalized;
        }
        if (servers instanceof Collection<?> collection) {
            for (Object item : collection) {
                addServer(normalized, Objects.toString(item, ""));
            }
            return normalized;
        }
        throw new IllegalArgumentException("servers must be a string or collection");
    }

    private static void addServer(List<String> servers, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.isEmpty()) {
            servers.add(value);
        }
    }

    private static Map<String, Object> readJsonMap(byte[] payload) {
        try {
            return JSON.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid json payload", exception);
        }
    }

    private static HttpRequest.BodyPublisher bodyPublisher(String method, byte[] body) {
        String normalized = method.toUpperCase(Locale.ROOT);
        if ("GET".equals(normalized) || "HEAD".equals(normalized)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body);
    }

    private static void copyRequestHeaders(Headers source, HttpRequest.Builder target) {
        source.forEach((name, values) -> {
            if (EXCLUDED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            for (String value : values) {
                target.header(name, value);
            }
        });
    }

    private static void relayResponse(HttpExchange exchange, HttpResponse<byte[]> response) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        response.headers().map().forEach((name, values) -> {
            if (EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            headers.put(name, new ArrayList<>(values));
        });
        byte[] body = response.body() == null ? new byte[0] : response.body();
        exchange.sendResponseHeaders(response.statusCode(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        byte[] body = JSON.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        List<String> encoded = new ArrayList<>();
        for (String part : parts) {
            encoded.add(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encoded);
    }
}
