/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for MCP client instances.
 * <p>
 * Built-in transport types are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.foundation.tool.mcp.McpClientProvider}.
 * Service adapters can register additional types via
 * {@link #register(String, McpClientProvider)} without modifying Core source.
 * <p>
 * Calling point: {@code ToolMgr.createClient()} delegates to this factory.
 *
 * @since 0.1.12
 * @see McpClientProvider
 * @see McpClient
 */
public final class McpClientFactory {
    private static final Map<String, McpClientProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // Discover and register providers via ServiceLoader
        for (McpClientProvider provider : ServiceLoader.load(McpClientProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private McpClientFactory() {
    }

    /**
     * Register an MCP client provider for a given type name.
     *
     * @param type     the transport type name (e.g. "sse", "custom_http")
     * @param provider the provider that creates McpClient instances
     */
    public static void register(String type, McpClientProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * Create an MCP client from a server configuration.
     * <p>
     * The {@code clientType} field in the config determines which provider is used.
     * Falls back to "sse" if no client type is specified.
     *
     * @param config the MCP server configuration
     * @return a new McpClient instance
     * @throws UnsupportedOperationException if no provider is registered for the type
     */
    public static McpClient create(McpServerConfig config) {
        String clientType = config.getClientType() == null
                ? "sse"
                : config.getClientType().toLowerCase(Locale.ROOT);
        // Normalize streamable-http to streamable_http
        if ("streamable-http".equals(clientType)) {
            clientType = "streamable_http";
        }
        McpClientProvider provider = REGISTRY.get(clientType);
        if (provider == null) {
            throw new UnsupportedOperationException(
                    "Unsupported MCP client type: " + config.getClientType());
        }
        return provider.create(config);
    }

    /**
     * Check whether a provider is registered for the given type.
     *
     * @param type the transport type name
     * @return true if a provider exists
     */
    public static boolean hasProvider(String type) {
        if (type == null) {
            return false;
        }
        return REGISTRY.containsKey(type.toLowerCase(Locale.ROOT));
    }
}
