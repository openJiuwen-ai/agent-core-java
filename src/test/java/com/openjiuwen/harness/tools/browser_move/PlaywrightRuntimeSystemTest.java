package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's test_playwright_runtime.py.
 * System test for the browser_move runtime flow.
 */
@Tag("system-test")
class PlaywrightRuntimeSystemTest {

    static boolean systemTestsEnabled() {
        String value = System.getenv("RUN_BROWSER_MOVE_SYSTEM_TESTS");
        if (value == null) {
            value = System.getProperty("RUN_BROWSER_MOVE_SYSTEM_TESTS");
        }
        return value != null && value.strip().toLowerCase().matches("1|true|yes|on");
    }

    private Map<String, Object> runLiveCheck(String query, String sessionId) throws Exception {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings();
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new RuntimeException("Missing API key for runtime example.");
        }

        BrowserAgentRuntime runtime = new BrowserAgentRuntime(
                settings.provider(),
                settings.apiKey(),
                settings.apiBase(),
                settings.modelName(),
                settings.mcpCfg(),
                settings.guardrails()
        );

        try {
            runtime.ensureStarted();
            Map<String, Object> result = runtime.runBrowserTask(query, sessionId, null, null);
            boolean ok = Boolean.TRUE.equals(result.getOrDefault("ok", false));
            return Map.of(
                    "ok", ok,
                    "mode", "live",
                    "session_id", result.getOrDefault("session_id", ""),
                    "request_id", result.getOrDefault("request_id", ""),
                    "final", result.getOrDefault("final", ""),
                    "error", result.get("error")
            );
        } finally {
            runtime.getService().started = false;
        }
    }

    @Test
    @DisabledIf("systemTestsEnabled")
    void testPlaywrightRuntimeHealthCheck() {
        McpServerConfig mcpCfg = BrowserRuntimeConfig.buildPlaywrightMcpConfig(180);
        BrowserRunGuardrails guardrails = new BrowserRunGuardrails(20, 2, 180, true);
        BrowserAgentRuntime runtime = new BrowserAgentRuntime(
                "openai", "test-key", "https://example.invalid/v1",
                "test-model", mcpCfg, guardrails
        );

        assertFalse(runtime.isRuntimeReady());
        runtime.ensureStarted();
        assertTrue(runtime.isRuntimeReady());

        Map<String, Object> health = runtime.health();
        assertTrue((Boolean) health.getOrDefault("started", false));
        assertTrue((Boolean) health.getOrDefault("runtime_ready", false));
        assertEquals(2, health.get("bridge_tools_registered"));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("systemTestsEnabled")
    void testPlaywrightRuntimeEndToEnd() throws Exception {
        Map<String, Object> result = runLiveCheck(
                "Go to https://example.com and return the page title.",
                "system-test-browser-runtime"
        );

        assertTrue((Boolean) result.get("ok"));
        assertEquals("live", result.get("mode"));
        assertEquals("system-test-browser-runtime", result.get("session_id"));
        assertNull(result.get("error"));
        assertFalse(String.valueOf(result.get("final")).isBlank());
    }
}
