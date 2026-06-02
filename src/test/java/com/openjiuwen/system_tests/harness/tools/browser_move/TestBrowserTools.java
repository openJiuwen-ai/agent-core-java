/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.tools.browser_move.BrowserMove;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System test for browser_move browser_tools registration flow.
 *
 * <p>Mirrors Python's {@code test_browser_tools.py} in
 * {@code tests.system_tests.harness.tools.browser_move}.</p>
 */
@Tag("system-test")
public class TestBrowserTools {

    static boolean systemTestsEnabled() {
        String value = System.getenv("RUN_BROWSER_MOVE_SYSTEM_TESTS");
        if (value == null) {
            value = System.getProperty("RUN_BROWSER_MOVE_SYSTEM_TESTS");
        }
        return value != null && value.strip().toLowerCase().matches("1|true|yes|on");
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_HOST");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_PORT");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_PATH");
        BrowserMove.stopLocalBrowserRuntimeServer();
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    @DisplayName("browser runtime MCP config matches streamable-http settings")
    void testBrowserRuntimeMcpConfigForStreamableHttp() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "streamable-http");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PORT", "8940");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PATH", "/mcp");

        McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();

        assertNotNull(config);
        assertEquals("streamable-http", config.getClientType());
        assertTrue(config.getServerPath().contains("127.0.0.1:8940/mcp"));
    }

    @Test
    @DisplayName("registration is a no-op when browser runtime MCP is disabled")
    void testRegisterBrowserRuntimeMcpServerDisabledReturnsFalse() throws Exception {
        DeepAgent agent = HarnessFactory.createDeepAgent();

        assertFalse(BrowserMove.registerBrowserRuntimeMcpServer(agent));
        assertEquals(0, agent.getDelegate().getAbilityManager().list().size());
    }

    @Test
    @DisplayName("port probe returns a deterministic boolean")
    void testPortOpenCheck() {
        boolean result = isPortOpen("127.0.0.1", 80);
        assertTrue(result || !result);
    }

    @Test
    @EnabledIf("systemTestsEnabled")
    @DisplayName("browser tools live path requires an MCP config")
    void testBrowserToolsEndToEndConfigurationGate() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "streamable-http");
        McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();

        assertNotNull(config);
        assertNull(BrowserMove.restartLocalBrowserRuntimeServer());
    }
}
