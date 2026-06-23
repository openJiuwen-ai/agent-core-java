/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.provider;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient;

/**
 * Built-in MCP client provider for Playwright transport.
 * <p>
 * Creates MCP clients that interact with Playwright-based MCP servers
 * for browser automation and web testing scenarios.
 *
 * @since 0.1.12
 * @see McpClientProvider
 * @see com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient
 */
public final class PlaywrightMcpClientProvider implements McpClientProvider {
    /**
     * Returns the Playwright transport type name.
     *
     * @return the type name "playwright"
     */
    @Override
    public String typeName() {
        return "playwright";
    }

    /**
     * Creates an MCP client using Playwright transport.
     *
     * @param config the MCP server configuration
     * @return a new PlaywrightClient instance
     */
    @Override
    public McpClient create(McpServerConfig config) {
        return new PlaywrightClient(config);
    }
}
