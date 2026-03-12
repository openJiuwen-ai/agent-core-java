/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAPI-file backed MCP-style client.
 */
public class OpenApiClient implements McpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, Operation> operations = new LinkedHashMap<>();

    public OpenApiClient(McpServerConfig config) {
        this.config = config;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        operations.clear();
        for (String rawPath : config.getServerPath().split(",")) {
            loadSpec(rawPath.trim());
        }
        return true;
    }

    @Override
    public boolean disconnect(float timeout) {
        operations.clear();
        return true;
    }

    @Override
    public List<Object> listTools(float timeout) {
        return new ArrayList<>(operations.values().stream().map(Operation::card).toList());
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        Operation operation = operations.get(toolName);
        if (operation == null) {
            throw new IllegalArgumentException("OpenAPI tool not found: " + toolName);
        }
        String url = resolveUrl(operation, arguments);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");
        if (timeout != McpServerConfig.NO_TIMEOUT && timeout > 0) {
            builder.timeout(Duration.ofMillis((long) (timeout * 1000)));
        }
        if ("GET".equals(operation.method)) {
            builder.GET();
        } else {
            Map<String, Object> body = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
            builder.method(operation.method, HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) {
        return Optional.ofNullable(operations.get(toolName)).map(Operation::card);
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    @SuppressWarnings("unchecked")
    private void loadSpec(String rawPath) throws Exception {
        Path path = Path.of(rawPath).toAbsolutePath();
        String content = Files.readString(path);
        Map<String, Object> spec = rawPath.endsWith(".json")
                ? MAPPER.readValue(content, new TypeReference<>() {
                })
                : new Yaml().load(content);

        String baseUrl = extractBaseUrl(spec);
        Map<String, Object> paths = spec.get("paths") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            if (!(pathEntry.getValue() instanceof Map<?, ?> methodsMap)) {
                continue;
            }
            for (Map.Entry<String, Object> methodEntry : castMap(methodsMap).entrySet()) {
                if (!(methodEntry.getValue() instanceof Map<?, ?> operationMap)) {
                    continue;
                }
                Map<String, Object> operation = castMap(operationMap);
                Object operationId = operation.get("operationId");
                String toolName = operationId != null
                        ? String.valueOf(operationId)
                        : methodEntry.getKey() + "_" + pathEntry.getKey().replace('/', '_')
                                .replace('{', '_').replace('}', '_');
                Map<String, Object> inputSchema = new LinkedHashMap<>();
                inputSchema.put("type", "object");
                inputSchema.put("properties", Map.of());
                Object summary = operation.get("summary");
                Object description = operation.get("description");
                operations.put(toolName, new Operation(
                        methodEntry.getKey().toUpperCase(),
                        pathEntry.getKey(),
                        baseUrl,
                        McpToolCard.builder()
                                .name(toolName)
                                .description(summary != null ? String.valueOf(summary)
                                        : description != null ? String.valueOf(description) : "")
                                .serverName(config.getServerName())
                                .serverId(config.getServerId())
                                .inputParams(inputSchema)
                                .build()
                ));
            }
        }
    }

    private String resolveUrl(Operation operation, Map<String, Object> arguments) {
        String path = operation.path;
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return operation.baseUrl + path;
    }

    @SuppressWarnings("unchecked")
    private String extractBaseUrl(Map<String, Object> spec) {
        Object serversObj = spec.get("servers");
        if (serversObj instanceof List<?> servers && !servers.isEmpty() && servers.get(0) instanceof Map<?, ?> server) {
            Object url = server.get("url");
            if (url != null) {
                return String.valueOf(url).replaceAll("/+$", "");
            }
        }
        Object explicitBaseUrl = config.getParams().get("base_url");
        return explicitBaseUrl == null ? "" : String.valueOf(explicitBaseUrl).replaceAll("/+$", "");
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private record Operation(String method, String path, String baseUrl, McpToolCard card) {
    }
}
