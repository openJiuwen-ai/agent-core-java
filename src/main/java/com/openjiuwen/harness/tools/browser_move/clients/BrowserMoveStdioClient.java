package com.openjiuwen.harness.tools.browser_move.clients;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;

/**
 * Mirrors Python's {@code BrowserMoveStdioClient} in
 * {@code openjiuwen.harness.tools.browser_move.clients.stdio_client}.
 */
public class BrowserMoveStdioClient extends StdioClient {

    public BrowserMoveStdioClient(McpServerConfig config) {
        super(config);
    }
}
