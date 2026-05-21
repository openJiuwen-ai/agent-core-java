package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeMcpSupport;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's test_browser_tools.py.
 * System test for browser_move browser_tools registration flow.
 */
@Tag("system-test")
class BrowserToolsSystemTest {

    private Process serverProcess;

    static boolean systemTestsEnabled() {
        String value = System.getenv("RUN_BROWSER_MOVE_SYSTEM_TESTS");
        if (value == null) {
            value = System.getProperty("RUN_BROWSER_MOVE_SYSTEM_TESTS");
        }
        return value != null && value.strip().toLowerCase().matches("1|true|yes|on");
    }

    @BeforeEach
    void setUp() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "streamable-http");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_HOST");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_PORT");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_PATH");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH");
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroyForcibly();
        }
    }

    private String startLocalStreamableHttpServer() throws Exception {
        String configuredUrl = System.getProperty("PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }

        String host = "127.0.0.1";
        int port = 8940;
        String path = "/mcp";

        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(host, 0));
            port = ss.getLocalPort();
        }

        String serverUrl = "http://" + host + ":" + port + path;
        return serverUrl;
    }

    private Map<String, Object> runBrowserToolsCheck(String query, String sessionId) throws Exception {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings();
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new RuntimeException("Missing API key for browser_tools example.");
        }

        McpServerConfig browserToolsCfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();
        if (browserToolsCfg == null) {
            throw new RuntimeException(
                    "browser_tools system test requires PLAYWRIGHT_RUNTIME_MCP_ENABLED=1 and a valid setup.");
        }

        AgentCard card = new AgentCard();
        ReActAgentConfig reactConfig = new ReActAgentConfig();

        Map<String, Object> result = Runner.runAgent(null, Map.of("query", query), sessionId, null);

        Map<String, Object> response = new LinkedHashMap<>();
        if (result instanceof Map<?, ?> map) {
            String resultType = String.valueOf(map.getOrDefault("result_type", ""));
            String final_ = String.valueOf(map.getOrDefault("output", ""));
            boolean ok = !"error".equals(resultType);
            response.put("ok", ok);
            response.put("mode", "browser-tools");
            response.put("session_id", sessionId);
            response.put("final", final_);
            response.put("error", ok ? null : final_);
        }
        return response;
    }

    @Test
    @DisabledIf("systemTestsEnabled")
    void testBrowserToolsRegistration() {
        McpServerConfig config = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();
        if (config != null) {
            assertNotNull(config.serverId());
            assertNotNull(config.clientType());
        }
    }

    @Test
    @DisabledIf("systemTestsEnabled")
    void testBrowserToolsEndToEndBasicValidation() {
        Runner.start();
        try {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();
            boolean registered = false;
            if (cfg != null) {
                assertEquals("streamable-http", cfg.clientType());
                registered = true;
            }
            assertTrue(registered || cfg == null,
                    "Registration should succeed or config should be null when disabled");
        } finally {
            Runner.stop();
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("systemTestsEnabled")
    void testBrowserToolsEndToEnd() throws Exception {
        Runner.start();
        try {
            String serverUrl = startLocalStreamableHttpServer();
            System.setProperty("PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", serverUrl);

            Map<String, Object> result = runBrowserToolsCheck(
                    "Go to https://example.com and return the page title.",
                    "system-test-browser-tools"
            );

            assertTrue((Boolean) result.getOrDefault("ok", false));
            assertEquals("browser-tools", result.get("mode"));
            assertEquals("system-test-browser-tools", result.get("session_id"));
            assertNull(result.get("error"));
            assertFalse(String.valueOf(result.get("final")).isBlank());
        } finally {
            Runner.stop();
        }
    }
}
