package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeMcpSupport;

import java.nio.file.Path;

/**
 * Mirrors Python's package entry helpers in
 * {@code openjiuwen.harness.tools.browser_move.__init__}.
 */
public final class BrowserMove {

    public static final Path REPO_ROOT = Path.of("").toAbsolutePath();

    private static String lastBrowserRuntimeUrl;

    private BrowserMove() {
    }

    public static McpServerConfig buildBrowserRuntimeMcpConfig() {
        return BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();
    }

    public static boolean registerBrowserRuntimeMcpServer(DeepAgent agent) throws Exception {
        return BrowserRuntimeMcpSupport.registerBrowserRuntimeMcpServer(agent);
    }

    public static boolean registerBrowserRuntimeMcpServer(DeepAgent agent, String tag) throws Exception {
        return BrowserRuntimeMcpSupport.registerBrowserRuntimeMcpServer(agent, tag);
    }

    public static String restartLocalBrowserRuntimeServer() {
        McpServerConfig config = buildBrowserRuntimeMcpConfig();
        if (config == null) {
            lastBrowserRuntimeUrl = null;
            return null;
        }
        String clientType = String.valueOf(config.getClientType() == null ? "" : config.getClientType()).trim().toLowerCase();
        if (!"sse".equals(clientType) && !"streamable-http".equals(clientType) && !"streamable_http".equals(clientType) && !"http".equals(clientType)) {
            lastBrowserRuntimeUrl = null;
            return null;
        }
        String previousUrl = lastBrowserRuntimeUrl;
        boolean hadLocalServer = previousUrl != null && !previousUrl.isBlank();
        stopLocalBrowserRuntimeServer();
        if (!hadLocalServer) {
            return null;
        }
        String serverPath = config.getServerPath();
        lastBrowserRuntimeUrl = serverPath;
        return serverPath;
    }

    public static void stopLocalBrowserRuntimeServer() {
        lastBrowserRuntimeUrl = null;
    }
}
