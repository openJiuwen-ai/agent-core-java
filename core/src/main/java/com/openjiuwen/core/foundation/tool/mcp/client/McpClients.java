/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.Locale;
import java.util.Map;

/**
 * Package-level facade for registering MCP clients with the global client registry.
 */
public final class McpClients {
    private static final String CLIENT_TYPE = "mcp";

    private McpClients() {
    }

    public static synchronized void registerDefaults() {
        ClientRegistry registry = ClientRegistry.getClientRegistry();
        registerIfMissing(registry, "sse", kwargs -> new SseClient(requiredConfig(kwargs)));
        registerIfMissing(registry, "stdio", kwargs -> new StdioClient(requiredConfig(kwargs)));
        registerIfMissing(registry, "streamable-http", kwargs -> new StreamableHttpClient(requiredConfig(kwargs)));
        registerIfMissing(registry, "streamable_http", kwargs -> new StreamableHttpClient(requiredConfig(kwargs)));
        registerIfMissing(registry, "openapi", kwargs -> new OpenApiClient(requiredConfig(kwargs)));
        registerIfMissing(registry, "playwright", kwargs -> new PlaywrightClient(requiredConfig(kwargs)));
    }

    public static String normalizeClientType(String clientType) {
        if (clientType == null || clientType.trim().isEmpty()) {
            return "sse";
        }
        String trimmed = clientType.trim();
        String camelSplit = trimmed.replaceAll("([a-z])([A-Z])", "$1-$2");
        String normalized = camelSplit.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("mcp-")) {
            normalized = normalized.substring("mcp-".length());
        }
        return switch (normalized) {
            case "sse" -> "sse";
            case "stdio" -> "stdio";
            case "streamable-http" -> "streamable-http";
            case "openapi", "open-api" -> "openapi";
            case "playwright" -> "playwright";
            default -> normalized;
        };
    }

    private static void registerIfMissing(ClientRegistry registry, String name, ClientRegistry.ClientFactory factory) {
        String key = CLIENT_TYPE + "_" + name;
        if (!registry.listClients().contains(key)) {
            registry.registerClient(name, CLIENT_TYPE, factory);
        }
    }

    private static McpServerConfig requiredConfig(Map<String, Object> kwargs) {
        Object value = kwargs != null ? kwargs.get("config") : null;
        if (value instanceof McpServerConfig config) {
            return config;
        }
        String actualType = value == null ? "null" : value.getClass().getName();
        throw new IllegalArgumentException(
                "MCP client factory requires kwargs['config'] to be McpServerConfig, actual: " + actualType);
    }
}
