/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's CLI entrypoint behavior in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/main.py}.
 */
class PlaywrightRuntimeMainTest {

    @Test
    void validatesApiKeyBeforeBuildingRuntime() {
        RuntimeSettings settings = settings("");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PlaywrightRuntimeMain.buildRuntime(settings)
        );

        assertEquals(BrowserMoveEnv.MISSING_API_KEY_MESSAGE, error.getMessage());
    }

    @Test
    void resolvesQueryAndDefaultSessionLikePythonEntrypoint() {
        assertEquals("open page", PlaywrightRuntimeMain.resolveInitialQuery(" open page "));
        assertEquals("", PlaywrightRuntimeMain.resolveInitialQuery(null));
        assertEquals("demo-browser-session", PlaywrightRuntimeMain.resolveSessionId(" "));
        assertEquals("session-1", PlaywrightRuntimeMain.resolveSessionId(" session-1 "));
    }

    @Test
    void singleQueryRunsTaskUnlessEmptyOrExit() {
        FakeRuntime runtime = new FakeRuntime();

        Map<String, Object> result = PlaywrightRuntimeMain.runSingleQuery(
                runtime,
                settings("test-key"),
                " inspect site ",
                ""
        );

        assertTrue(runtime.started);
        assertEquals(1, runtime.runCount);
        assertEquals("inspect site", runtime.lastTask);
        assertEquals("demo-browser-session", runtime.lastSessionId);
        assertEquals("ok", result.get("status"));

        Map<String, Object> skipped = PlaywrightRuntimeMain.runSingleQuery(
                runtime,
                settings("test-key"),
                "quit",
                "session"
        );
        assertEquals("exit", skipped.get("reason"));
    }

    @Test
    void interactiveLoopStopsOnExitAndShutsDown() {
        FakeRuntime runtime = new FakeRuntime();
        ByteArrayInputStream input = new ByteArrayInputStream("first task\nexit\n".getBytes(StandardCharsets.UTF_8));

        PlaywrightRuntimeMain.runInteractive(runtime, settings("test-key"), "", "session-x", input);

        assertTrue(runtime.started);
        assertTrue(runtime.shutdown);
        assertEquals(1, runtime.runCount);
        assertEquals("first task", runtime.lastTask);
        assertEquals("session-x", runtime.lastSessionId);
    }

    @Test
    void emptySingleQueryDoesNotStartRuntime() {
        FakeRuntime runtime = new FakeRuntime();

        Map<String, Object> result = PlaywrightRuntimeMain.runSingleQuery(runtime, settings("test-key"), "", null);

        assertFalse(runtime.started);
        assertEquals("empty_query", result.get("reason"));
    }

    private static RuntimeSettings settings(String apiKey) {
        McpServerConfig mcpConfig = McpServerConfig.builder()
                .serverId("playwright_official_stdio")
                .serverName("playwright-official")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("command", "npx", "args", java.util.List.of("@playwright/mcp@latest")))
                .build();
        return new RuntimeSettings(
                "openai",
                apiKey,
                "https://api.openai.com/v1",
                "gpt-4.1-mini",
                mcpConfig,
                new BrowserRunGuardrails()
        );
    }

    private static final class FakeRuntime implements PlaywrightRuntimeMain.RuntimeOperations {
        private boolean started;
        private boolean shutdown;
        private int runCount;
        private String lastTask;
        private String lastSessionId;

        @Override
        public void ensureStarted() {
            started = true;
        }

        @Override
        public Map<String, Object> runBrowserTask(String task, String sessionId) {
            runCount++;
            lastTask = task;
            lastSessionId = sessionId;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("task", task);
            result.put("session_id", sessionId);
            return result;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }
    }
}
