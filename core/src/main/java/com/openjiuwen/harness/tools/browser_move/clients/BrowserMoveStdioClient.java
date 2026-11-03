/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.clients;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;

/**
 * Browser runtime MCP stdio client.
 *
 * <p>Mirrors Python's {@code BrowserMoveStdioClient} in
 * {@code openjiuwen/harness/tools/browser_move/clients/stdio_client.py}.</p>
 */
public class BrowserMoveStdioClient extends StdioClient {

    public BrowserMoveStdioClient(McpServerConfig config) {
        super(config);
    }

    public boolean ping() {
        return true;
    }
}
