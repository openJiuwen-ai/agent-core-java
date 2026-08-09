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
import java.math.BigDecimal;
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

    protected final McpServerConfig config;
    protected final HttpClient httpClient;
    protected final AtomicLong requestCounter = new AtomicLong();
    protected volatile boolean connected;

    protected AbstractHttpMcpClient(McpServerConfig config) {
        this.config = config;
        this.httpClient = resolveHttpClient(config);
    }

    /**
     * Prefer runtime-injected {@code _ojw_http_client} from {@link McpServerConfig#getParams()}
     * for outbound TLS/auth control; otherwise create a JDK client with connect timeout.
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

    private static long resolveConnectTimeout(McpServerConfig config) {
        if (config != null && config.getConnectTimeoutSeconds() != null && config.getConnectTimeoutSeconds() > 0) {
            return config.getConnectTimeoutSeconds().longValue();
        }
        return DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    /**
     * Performs the MCP initialize handshake with retries.
     * On failure, rolls {@code connected} back to {@code false} and returns {@code false}.
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
                return false;
            } catch (IOException | RuntimeException e) {
                this.connected = false;
                if (i >= maxAttempts) {
                    break;
                }
                try {
                    Thread.sleep(100L * (i + 1));
                } catch (InterruptedException interrupted) {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public boolean disconnect(float timeout) {
        this.connected = false;
        return true;
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        Map<String, Object> result = callRpc("tools/list", Map.of(), timeout);
        List<Object> tools = new ArrayList<>();
        for (Map<String, Object> item : asListOfMaps(result.get("tools"))) {
            tools.add(toToolCard(item));
        }
        return tools;
    }

    @Override
    public List<Object> listResources(float timeout) throws Exception {
        Map<String, Object> result = callRpc("resources/list", Map.of(), timeout);
        return new ArrayList<>(asListOfMaps(result.get("resources")));
    }

    @Override
    public List<Object> readResource(String uri, float timeout) throws Exception {
        Map<String, Object> result = callRpc("resources/read", Map.of("uri", uri), timeout);
        return new ArrayList<>(asListOfMaps(result.get("contents")));
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        Map<String, Object> result = callRpc("tools/call",
                Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments), timeout);
        Object flattened = flattenToolTextContent(result.get("content"));
        return flattened != null ? flattened : result;
    }

    private static Object flattenToolTextContent(Object content) {
        if (!(content instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<String> textParts = collectTextParts(list);
        if (textParts.size() == 1) {
            return textParts.get(0);
        }
        if (!textParts.isEmpty()) {
            return String.join("\n", textParts);
        }
        return null;
    }

    private static List<String> collectTextParts(List<?> list) {
        List<String> textParts = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object text = map.get("text");
            if (text != null) {
                textParts.add(String.valueOf(text));
            }
        }
        return textParts;
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

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

    protected void sendNotification(String method, Map<String, Object> params, float timeout)
            throws IOException, InterruptedException {
        ensureConnected();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("method", method);
        requestBody.put("params", params == null ? Map.of() : params);
        postJsonRpc(requestBody, timeout);
    }

    private Map<String, Object> postJsonRpc(Map<String, Object> requestBody, float timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(withAuthQuery(config.getServerPath())))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers
                        .ofString(MAPPER.writeValueAsString(requestBody), StandardCharsets.UTF_8));
        if (Float.compare(timeout, McpServerConfig.NO_TIMEOUT) != 0 && timeout > 0) {
            long timeoutMillis = BigDecimal.valueOf(timeout).multiply(BigDecimal.valueOf(1000L)).longValue();
            builder.timeout(Duration.ofMillis(timeoutMillis));
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

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("MCP client is not connected: " + config.getServerPath());
        }
    }

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

    protected static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? castMap(map) : null;
    }

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

    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
