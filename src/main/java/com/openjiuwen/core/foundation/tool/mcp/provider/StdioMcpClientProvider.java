/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.provider;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;

/**
 * Built-in MCP client provider for Stdio transport.
 * <p>
 * Creates MCP clients that communicate with MCP servers via standard input/output
 * streams, typically used for local subprocess-based MCP servers.
 *
 * @since 0.1.12
 * @see McpClientProvider
 * @see com.openjiuwen.core.foundation.tool.mcp.client.StdioClient
 */
public final class StdioMcpClientProvider implements McpClientProvider {
    /**
     * Returns the Stdio transport type name.
     *
     * @return the type name "stdio"
     */
    @Override
    public String typeName() {
        return "stdio";
    }

    /**
     * Creates an MCP client using Stdio transport.
     *
     * @param config the MCP server configuration
     * @return a new StdioClient instance
     */
    @Override
    public McpClient create(McpServerConfig config) {
        return new StdioClient(config);
    }
}
