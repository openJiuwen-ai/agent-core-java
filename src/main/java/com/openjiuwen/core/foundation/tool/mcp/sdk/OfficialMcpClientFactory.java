/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.sdk;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Internal factory that keeps official Java SDK transport selection inside the MCP layer.
 *
 * @since 0.1.7
 */
public final class OfficialMcpClientFactory {
    private OfficialMcpClientFactory() {
    }

    /**
     * Creates an MCP client backed by the official Java SDK transport implementation.
     *
     * @param config MCP server config
     * @return SDK-backed MCP client
     */
    public static McpClient create(McpServerConfig config) {
        return new OfficialSdkMcpClient(config, map(config));
    }

    /**
     * Checks whether the client type is supported by the official SDK adapter.
     *
     * @param clientType client type from config
     * @return {@code true} when supported
     */
    public static boolean supports(String clientType) {
        return switch (normalizeClientType(clientType)) {
            case "stdio", "sse", "streamable_http" -> true;
            default -> false;
        };
    }

    /**
     * Normalizes aliases to the transport names used inside the MCP layer.
     *
     * @param clientType client type from config
     * @return normalized transport name
     */
    public static String normalizeClientType(String clientType) {
        if (clientType == null || clientType.isBlank()) {
            return "sse";
        }
        String normalized = clientType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "http", "streamable-http", "streamable_http" -> "streamable_http";
            default -> normalized;
        };
    }

    static OfficialTransportConfig map(McpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("McpServerConfig cannot be null");
        }
        String clientType = normalizeClientType(config.getClientType());
        Map<String, Object> params = config.getParams() == null ? Map.of() : config.getParams();
        return switch (clientType) {
            case "stdio" -> mapStdio(config, params);
            case "sse" -> mapHttp(config, OfficialTransportType.SSE);
            case "streamable_http" -> mapHttp(config, OfficialTransportType.STREAMABLE_HTTP);
            default -> throw new IllegalArgumentException("Unsupported MCP client type: " + config.getClientType());
        };
    }

    private static OfficialTransportConfig mapStdio(McpServerConfig config, Map<String, Object> params) {
        String command = params.containsKey("command")
                ? String.valueOf(params.get("command"))
                : config.getServerPath();
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("stdio MCP server requires params.command or serverPath");
        }
        return new OfficialTransportConfig(
                OfficialTransportType.STDIO,
                config.getServerPath(),
                command,
                normalizeArgs(params.get("args")),
                normalizeStringMap(params.get("env")),
                params.get("cwd") == null ? null : String.valueOf(params.get("cwd")),
                copyMap(config.getAuthHeaders())
        );
    }

    private static OfficialTransportConfig mapHttp(McpServerConfig config, OfficialTransportType transportType) {
        if (config.getServerPath() == null || config.getServerPath().isBlank()) {
            throw new IllegalArgumentException("HTTP MCP server requires serverPath");
        }
        return new OfficialTransportConfig(
                transportType,
                config.getServerPath(),
                null,
                List.of(),
                Map.of(),
                null,
                copyMap(config.getAuthHeaders())
        );
    }

    private static List<String> normalizeArgs(Object rawArgs) {
        if (!(rawArgs instanceof List<?> args)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object arg : args) {
            result.add(String.valueOf(arg));
        }
        return List.copyOf(result);
    }

    private static Map<String, String> normalizeStringMap(Object rawMap) {
        if (!(rawMap instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> copyMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }

    record OfficialTransportConfig(
            OfficialTransportType transportType,
            String serverPath,
            String command,
            List<String> args,
            Map<String, String> env,
            String cwd,
            Map<String, String> authHeaders
    ) {
    }
}
