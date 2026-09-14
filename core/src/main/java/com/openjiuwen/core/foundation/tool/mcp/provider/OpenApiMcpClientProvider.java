/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.provider;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient;

/**
 * Built-in MCP client provider for OpenAPI specification transport.
 * <p>
 * Creates MCP clients that interact with MCP servers exposing tools
 * defined by OpenAPI specifications, enabling REST API integration.
 * 
 * @see McpClientProvider
 * @see com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient
 * @since 0.1.7
 */
public final class OpenApiMcpClientProvider implements McpClientProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String typeName() {
        return "openapi";
    }

    /**
     * Creates an MCP client using OpenAPI specification.
     * 
     * @param config the MCP server configuration
     * @return a new OpenApiClient instance
     * @since 0.1.7
     */
    @Override
    public McpClient create(McpServerConfig config) {
        return new OpenApiClient(config);
    }
}
