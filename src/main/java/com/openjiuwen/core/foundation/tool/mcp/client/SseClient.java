/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

/**
 * Java baseline SSE MCP client.
 *
 * <p>Current implementation uses HTTP JSON-RPC requests to the configured endpoint,
 * which is sufficient for MCP servers exposing SSE-compatible RPC endpoints.</p>
 */
public class SseClient extends AbstractHttpMcpClient {

    public SseClient(McpServerConfig config) {
        super(config);
    }
}
