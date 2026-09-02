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
 * @see McpClientProvider
 * @see com.openjiuwen.core.foundation.tool.mcp.client.StdioClient
 * @since 0.1.7
 */
public final class StdioMcpClientProvider implements McpClientProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
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
     * @since 0.1.7
     */
    @Override
    public McpClient create(McpServerConfig config) {
        return new StdioClient(config);
    }
}
