/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.provider;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * @return a new SseClient instance adapted to the McpClient interface
     */
    @Override
    public McpClient create(McpServerConfig config) {
        SseClient sseClient = new SseClient(config);
        return new McpClient() {
            @Override
            public boolean connect(int retryTimes, float timeout) throws Exception {
                return sseClient.connect(retryTimes, timeout).join();
            }

            @Override
            public boolean disconnect(float timeout) throws Exception {
                return sseClient.disconnect(timeout).join();
            }

            @Override
            public List<Object> listTools(float timeout) throws Exception {
                return sseClient.listTools(timeout).join();
            }

            @Override
            public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
                return sseClient.callTool(toolName, arguments, timeout).join();
            }

            @Override
            public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
                return sseClient.getToolInfo(toolName, timeout).join();
            }

            @Override
            public String getServerPath() {
                return sseClient.getServerPath();
            }
        };
    }
}
