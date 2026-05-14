package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

/**
 * Mirrors Python's {@code RuntimeSettings} in browser_move config.
 */
public record RuntimeSettings(
        String provider,
        String apiKey,
        String apiBase,
        String modelName,
        McpServerConfig mcpCfg,
        BrowserRunGuardrails guardrails
) {
}
