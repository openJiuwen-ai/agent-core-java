/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for HTTP-based MCP transports.
 */
abstract class AbstractHttpMcpClient implements McpClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L;

    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected final McpServerConfig config;

    /**
     * httpClient.
     * 
     * @since 0.1.7
     */
    protected final HttpClient httpClient;

    /**
     * requestCounter.
     * 
     * @since 0.1.7
     */
    protected final AtomicLong requestCounter = new AtomicLong();

    /**
     * connected.
     * 
     * @since 0.1.7
     */
    protected volatile boolean connected;

    /**
     * AbstractHttpMcpClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    protected AbstractHttpMcpClient(McpServerConfig config) {
        this.config = config;
        this.httpClient = resolveHttpClient(config);
    }

    /**
     * Builds an {@link HttpClient} with connect timeout from config (default 10s),
     * or returns an injected client from {@code params._ojw_http_client} when present.
     *
     * @param config MCP server config providing timeout / optional injected client
     * @return HttpClient used for JSON-RPC posts
     * @since 0.1.7
     */
    private static HttpClient resolveHttpClient(McpServerConfig config) {
        if (config != null && config.getParams() != null) {
            Object injected = config.getParams().get("_ojw_http_client");
            if (injected instanceof HttpClient client) {
                return client;
            }
        }
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(resolveConnectTimeout(config))).build();
    }

    /**
     * Resolves HTTP connect timeout in seconds from config, falling back to the default.
     *
     * @param config MCP server config; may be null
     * @return positive connect timeout in seconds
     * @since 0.1.14
     */
    private static long resolveConnectTimeout(McpServerConfig config) {
        if (config != null && config.getConnectTimeoutSeconds() != null && config.getConnectTimeoutSeconds() > 0) {
            return config.getConnectTimeoutSeconds().longValue();
        }
        return DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    /**
     * Performs the MCP initialize handshake with retries.
     * <p>
     * On failure, rolls {@code connected} back to {@code false} and returns {@code false}
     * (or rethrows after exhausting retries) instead of leaving a permanently-true connected flag.
     *
     * @param retryTimes additional attempts after the first try (total attempts = retryTimes + 1)
     * @param timeout per-attempt RPC timeout in seconds
     * @return {@code true} when initialize and {@code notifications/initialized} succeed; {@code false} after
     *         exhausted retries or interruption
     * @throws Exception declared for {@link McpClient} compatibility; this implementation returns {@code false}
     *         instead of propagating transport failures
     * @since 0.1.7
     */
    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        int maxAttempts = Math.max(0, retryTimes);
        for (int i = 0; i <= maxAttempts; i++) {
            this.connected = true;
            try {
                callRpc("initialize", Map.of("protocolVersion", "2024-11-05", "clientInfo",
                        Map.of("name", "agent-core-java", "version", "0.1.7"), "capabilities", Map.of()), timeout);
                sendNotification("notifications/initialized", Map.of(), timeout);
                return true;
            } catch (InterruptedException e) {
                this.connected = false;
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException | RuntimeException e) {
                this.connected = false;
                if (i >= maxAttempts) {
                    break;
                }
                try {
                    Thread.sleep(100L * (i + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * disconnect.
     * 
     * @param timeout timeout
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean disconnect(float timeout) {
        this.connected = false;
        return true;
    }

    /**
     * listTools.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> listTools(float timeout) throws Exception {
        Map<String, Object> result = callRpc("tools/list", Map.of(), timeout);
        List<Object> tools = new ArrayList<>();
        for (Map<String, Object> item : asListOfMaps(result.get("tools"))) {
            tools.add(toToolCard(item));
        }
        return tools;
    }

    /**
     * listResources.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> listResources(float timeout) throws Exception {
        Map<String, Object> result = callRpc("resources/list", Map.of(), timeout);
        return new ArrayList<>(asListOfMaps(result.get("resources")));
    }

    /**
     * readResource.
     * 
     * @param uri uri
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> readResource(String uri, float timeout) throws Exception {
        Map<String, Object> result = callRpc("resources/read", Map.of("uri", uri), timeout);
        return new ArrayList<>(asListOfMaps(result.get("contents")));
    }

    /**
     * Invokes an MCP tool and flattens text content blocks.
     * <p>
     * When the result {@code content} list has multiple text parts, they are joined with {@code \n};
     * a single text part is returned as a plain string; otherwise the raw result map is returned.
     *
     * @param toolName MCP tool name
     * @param arguments tool arguments; {@code null} is treated as empty
     * @param timeout RPC timeout in seconds
     * @return tool text output or the raw result map
     * @throws IOException when HTTP transport or JSON encode/decode fails
     * @throws InterruptedException when the HTTP call is interrupted
     * @throws IllegalStateException when not connected, response id mismatches, or RPC error is present
     * @throws Exception declared for {@link McpClient} compatibility
     * @since 0.1.7
     */
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        Map<String, Object> result = callRpc("tools/call",
                Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments), timeout);
        Object content = result.get("content");
        if (content instanceof List<?> list && !list.isEmpty()) {
            List<String> textParts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null) {
                        textParts.add(String.valueOf(text));
                    }
                }
            }
            if (textParts.size() == 1) {
                return textParts.get(0);
            }
            if (!textParts.isEmpty()) {
                return String.join("\n", textParts);
            }
        }
        return result;
    }

    /**
     * getToolInfo.
     * 
     * @param toolName toolName
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /**
     * getServerPath.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    /**
     * Sends a JSON-RPC request with an id and validates the response id.
     *
     * @param method JSON-RPC method name
     * @param params method params; {@code null} is treated as empty
     * @param timeout request timeout in seconds
     * @return the {@code result} object as a map (non-map results are wrapped under {@code result})
     * @throws IOException when HTTP transport or JSON encode/decode fails
     * @throws InterruptedException when the HTTP call is interrupted
     * @throws IllegalStateException when not connected, response id mismatches, or RPC error is present
     * @since 0.1.7
     */
    protected Map<String, Object> callRpc(String method, Map<String, Object> params, float timeout)
            throws IOException, InterruptedException {
        ensureConnected();
        long requestId = requestCounter.incrementAndGet();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", requestId);
        requestBody.put("method", method);
        requestBody.put("params", params == null ? Map.of() : params);

        Map<String, Object> payload = postJsonRpc(requestBody, timeout);
        Object responseId = payload.get("id");
        if (!(responseId instanceof Number number) || number.longValue() != requestId) {
            throw new IllegalStateException(
                    "JSON-RPC response id mismatch: expected=" + requestId + ", actual=" + responseId);
        }
        if (payload.containsKey("error")) {
            throw new IllegalStateException(String.valueOf(payload.get("error")));
        }
        Object result = payload.get("result");
        if (result instanceof Map<?, ?> map) {
            return castMap(map);
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("result", result);
        return wrapped;
    }

    /**
     * Sends a JSON-RPC notification (no {@code id} field).
     *
     * @param method notification method name (e.g. {@code notifications/initialized})
     * @param params notification params; {@code null} is treated as empty
     * @param timeout request timeout in seconds
     * @throws IOException when HTTP transport or JSON encode/decode fails
     * @throws InterruptedException when the HTTP call is interrupted
     * @throws IllegalStateException when the client is not connected
     * @since 0.1.14
     */
    protected void sendNotification(String method, Map<String, Object> params, float timeout)
            throws IOException, InterruptedException {
        ensureConnected();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("method", method);
        requestBody.put("params", params == null ? Map.of() : params);
        postJsonRpc(requestBody, timeout);
    }

    /**
     * POSTs a JSON-RPC body to the configured server path and parses the JSON response map.
     * <p>
     * Empty response bodies are treated as an empty map (typical for notifications).
     *
     * @param requestBody JSON-RPC request or notification payload
     * @param timeout request timeout in seconds; {@link McpServerConfig#NO_TIMEOUT} disables the request timeout
     * @return parsed response body as a map, or empty map when the body is blank
     * @throws IOException when serialization, HTTP I/O, or JSON parse fails
     * @throws InterruptedException when the HTTP call is interrupted
     * @since 0.1.14
     */
    private Map<String, Object> postJsonRpc(Map<String, Object> requestBody, float timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(withAuthQuery(config.getServerPath())))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers
                        .ofString(MAPPER.writeValueAsString(requestBody), StandardCharsets.UTF_8));
        if (Float.compare(timeout, McpServerConfig.NO_TIMEOUT) != 0 && timeout > 0) {
            builder.timeout(Duration.ofMillis((long) (timeout * 1000)));
        }
        for (Map.Entry<String, String> entry : config.getAuthHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        HttpResponse<String> response =
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body();
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(body, new TypeReference<>() {
        });
    }

    /**
     * toToolCard.
     * 
     * @param item item
     * @return the result
     * @since 0.1.7
     */
    protected McpToolCard toToolCard(Map<String, Object> item) {
        Map<String, Object> inputSchema = asMap(item.get("inputSchema"));
        if (inputSchema == null) {
            Map<String, Object> function = asMap(item.get("function"));
            inputSchema = function != null ? asMap(function.get("parameters")) : null;
        }
        Object name = item.get("name");
        Object description = item.get("description");
        return McpToolCard.builder().name(name != null ? String.valueOf(name) : "")
                .description(description != null ? String.valueOf(description) : "").serverName(config.getServerName())
                .serverId(config.getServerId()).inputParams(inputSchema != null ? inputSchema : Map.of()).build();
    }

    /**
     * ensureConnected.
     * 
     * @since 0.1.7
     */
    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("MCP client is not connected: " + config.getServerPath());
        }
    }

    /**
     * withAuthQuery.
     * 
     * @param url url
     * @return the result
     * @since 0.1.7
     */
    private String withAuthQuery(String url) {
        if (config.getAuthQueryParams() == null || config.getAuthQueryParams().isEmpty()) {
            return url;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : config.getAuthQueryParams().entrySet()) {
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return url + (url.contains("?") ? "&" : "?") + joiner;
    }

    /**
     * asMap.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? castMap(map) : null;
    }

    /**
     * asListOfMaps.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(castMap(map));
            }
        }
        return result;
    }

    /**
     * castMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
