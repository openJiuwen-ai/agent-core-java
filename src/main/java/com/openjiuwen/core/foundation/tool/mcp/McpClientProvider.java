/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

/**
 * Provider interface for creating MCP client instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.foundation.tool.mcp.McpClientProvider}.
 * Each provider declares which transport {@code typeName()} it supports.
 * Service adapters can also register providers programmatically via
 * {@link McpClientFactory#register(String, McpClientProvider)}.
 *
 * @since 0.1.12
 * @see McpClientFactory
 * @see McpClient
 */
public interface McpClientProvider {
    /**
     * The transport type name this provider handles (e.g., "sse", "stdio").
     *
     * @return the type name for registration
     */
    String typeName();

    /**
     * Create an MCP client for the given server configuration.
     *
     * @param config the MCP server configuration
     * @return a new McpClient instance
     */
    McpClient create(McpServerConfig config);
}
