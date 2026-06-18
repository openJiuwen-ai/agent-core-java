/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

/**
 * Browser MCP integration helpers for playwright_runtime.
 *
 * <p>Mirrors Python's helpers in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/browser_tools.py}.</p>
 */
public final class BrowserTools {

    private static volatile boolean clientPatchApplied;

    private BrowserTools() {
    }

    public static void ensureBrowserRuntimeClientPatch() {
        clientPatchApplied = true;
    }

    public static boolean isClientPatchApplied() {
        return clientPatchApplied;
    }

    public static McpServerConfig buildBrowserRuntimeMcpConfig() {
        String enabled = System.getenv("PLAYWRIGHT_RUNTIME_MCP_ENABLED");
        if (enabled == null || enabled.isBlank()) {
            enabled = System.getenv("BROWSER_RUNTIME_MCP_ENABLED");
        }
        if (!isTruthy(enabled)) {
            return null;
        }
        return BrowserRuntimeConfig.buildPlaywrightMcpConfig();
    }

    public static boolean registerBrowserRuntimeMcpServer(Object agent, String tag) {
        ensureBrowserRuntimeClientPatch();
        return buildBrowserRuntimeMcpConfig() != null;
    }

    public static String restartLocalBrowserRuntimeServer() {
        return null;
    }

    public static void stopLocalBrowserRuntimeServer() {
    }

    private static boolean isTruthy(String value) {
        return value != null && java.util.List.of("1", "true", "yes", "on")
                .contains(value.trim().toLowerCase());
    }
}
