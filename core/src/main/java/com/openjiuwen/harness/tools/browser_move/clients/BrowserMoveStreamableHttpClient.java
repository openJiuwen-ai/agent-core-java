/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.clients;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;

/**
 * Browser runtime MCP streamable HTTP client.
 *
 * <p>Mirrors Python's {@code BrowserMoveStreamableHttpClient} in
 * {@code openjiuwen/harness/tools/browser_move/clients/streamable_http_client.py}.</p>
 */
public class BrowserMoveStreamableHttpClient extends StreamableHttpClient {

    public BrowserMoveStreamableHttpClient(McpServerConfig config) {
        super(config);
    }
}
