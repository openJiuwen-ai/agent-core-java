/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

/**
 * HTTP JSON-RPC based MCP client for streamable-http servers.
 * 
 * @since 0.1.7
 */
public class StreamableHttpClient extends AbstractHttpMcpClient {
    /**
     * StreamableHttpClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    public StreamableHttpClient(McpServerConfig config) {
        super(config);
    }
}
