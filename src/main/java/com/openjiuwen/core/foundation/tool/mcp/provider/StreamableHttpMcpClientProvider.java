/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.provider;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;

/**
 * Built-in MCP client provider for Streamable HTTP transport.
 * <p>
 * Creates MCP clients that communicate with MCP servers using the Streamable HTTP
 * protocol, which supports bidirectional streaming over HTTP connections.
 *
 * @since 0.1.12
 * @see McpClientProvider
 * @see com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient
 */
public final class StreamableHttpMcpClientProvider implements McpClientProvider {
    /**
     * Returns the Streamable HTTP transport type name.
     *
     * @return the type name "streamable_http"
     */
    @Override
    public String typeName() {
        return "streamable_http";
    }

    /**
     * Creates an MCP client using Streamable HTTP transport.
     *
     * @param config the MCP server configuration
     * @return a new StreamableHttpClient instance
     */
    @Override
    public McpClient create(McpServerConfig config) {
        return new StreamableHttpClient(config);
    }
}
