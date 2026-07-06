/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.provider;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;

/**
 * Built-in MCP client provider for SSE (Server-Sent Events) transport.
 * <p>
 * Creates MCP clients that communicate with MCP servers over HTTP using
 * server-sent events for streaming responses. This is the default transport
 * when no client type is specified in the server configuration.
 *
 * @since 0.1.12
 * @see McpClientProvider
 * @see com.openjiuwen.core.foundation.tool.mcp.client.SseClient
 */
public final class SseMcpClientProvider implements McpClientProvider {
    /**
     * Returns the SSE transport type name.
     *
     * @return the type name "sse"
     */
    @Override
    public String typeName() {
        return "sse";
    }

    /**
     * Creates an MCP client using SSE transport.
     *
     * @param config the MCP server configuration
     * @return a new SseClient instance
     */
    @Override
    public McpClient create(McpServerConfig config) {
        return new SseClient(config);
    }
}
