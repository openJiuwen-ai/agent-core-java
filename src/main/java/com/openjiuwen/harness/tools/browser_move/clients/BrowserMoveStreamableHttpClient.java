package com.openjiuwen.harness.tools.browser_move.clients;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;

/**
 * Mirrors Python's {@code BrowserMoveStreamableHttpClient} in
 * {@code openjiuwen.harness.tools.browser_move.clients.streamable_http_client}.
 */
public class BrowserMoveStreamableHttpClient extends StreamableHttpClient {

    public BrowserMoveStreamableHttpClient(McpServerConfig config) {
        super(config);
    }
}
