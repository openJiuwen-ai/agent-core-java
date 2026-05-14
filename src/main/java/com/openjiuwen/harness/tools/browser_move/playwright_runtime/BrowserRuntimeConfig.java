package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal config helpers mirroring Python browser_move runtime config.
 */
public final class BrowserRuntimeConfig {

    public static final int DEFAULT_BROWSER_TIMEOUT_S = 180;
    public static final String DEFAULT_MODEL_NAME = "gpt-4o";

    private BrowserRuntimeConfig() {
    }

    public static RuntimeSettings buildRuntimeSettings() {
        BrowserRunGuardrails guardrails = new BrowserRunGuardrails();
        guardrails.setTimeoutS(DEFAULT_BROWSER_TIMEOUT_S);
        McpServerConfig mcpConfig = buildPlaywrightMcpConfig(DEFAULT_BROWSER_TIMEOUT_S);
        return new RuntimeSettings(
                "openai",
                "",
                "https://api.openai.com/v1",
                DEFAULT_MODEL_NAME,
                mcpConfig,
                guardrails
        );
    }

    public static McpServerConfig buildPlaywrightMcpConfig(int timeoutS) {
        Map<String, Object> params = new HashMap<>();
        params.put("cwd", Path.of("").toAbsolutePath().toString());
        params.put("timeout_s", timeoutS);
        return McpServerConfig.builder()
                .serverId("playwright-runtime")
                .serverName("playwright-runtime")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(params)
                .build();
    }
}
