/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's MCP tool service behavior in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime_mcp_server.py}.
 */
class PlaywrightRuntimeMcpServerTest {

    @AfterEach
    void tearDown() {
        PlaywrightRuntimeMcpServer.resetRuntimeForTesting();
    }

    @Test
    void resolvesSessionAndRequestFromExplicitValuesBeforeContext() {
        PlaywrightRuntimeMcpServer.McpContext ctx =
                new PlaywrightRuntimeMcpServer.McpContext("ctx-session", "ctx-request");

        assertEquals("explicit-session", PlaywrightRuntimeMcpServer.resolveSessionId(" explicit-session ", ctx));
        assertEquals("ctx-session", PlaywrightRuntimeMcpServer.resolveSessionId("", ctx));
        assertEquals("explicit-request", PlaywrightRuntimeMcpServer.resolveRequestId(" explicit-request ", ctx));
        assertEquals("ctx-request", PlaywrightRuntimeMcpServer.resolveRequestId("", ctx));
    }

    @Test
    void browserRunTaskUsesRuntimeAndStripsDataScreenshot() {
        FakeRuntime runtime = new FakeRuntime();
        PlaywrightRuntimeMcpServer.setRuntimeForTesting(runtime);

        Map<String, Object> result = PlaywrightRuntimeMcpServer.browserRunTask(
                "open site",
                "",
                "",
                0,
                new PlaywrightRuntimeMcpServer.McpContext("session-1", "request-1")
        );

        assertEquals("open site", runtime.lastTask);
        assertEquals("session-1", runtime.lastSessionId);
        assertEquals("request-1", runtime.lastRequestId);
        assertEquals("[screenshot saved]", result.get("screenshot"));
    }

    @Test
    void cancelAndClearRequireResolvedSessionId() {
        FakeRuntime runtime = new FakeRuntime();
        PlaywrightRuntimeMcpServer.setRuntimeForTesting(runtime);

        assertThrows(IllegalArgumentException.class,
                () -> PlaywrightRuntimeMcpServer.browserCancelTask("", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> PlaywrightRuntimeMcpServer.browserClearCancel("", "", null));

        Map<String, Object> cancel = PlaywrightRuntimeMcpServer.browserCancelTask("s1", "", null);
        Map<String, Object> clear = PlaywrightRuntimeMcpServer.browserClearCancel("", "", 
                new PlaywrightRuntimeMcpServer.McpContext("s2", "r2"));

        assertEquals("cancelled", cancel.get("status"));
        assertEquals("cleared", clear.get("status"));
    }

    @Test
    void customActionRetriesAfterRuntimeBinding() {
        FakeRuntime runtime = new FakeRuntime();
        PlaywrightRuntimeMcpServer.setRuntimeForTesting(runtime);

        Map<String, Object> result = PlaywrightRuntimeMcpServer.browserCustomAction(
                "ping",
                "session",
                "request",
                Map.of(),
                null
        );

        assertEquals(true, result.get("ok"));
        assertEquals("ping", result.get("action"));
    }

    @Test
    void parsesArgsAndResolvesStatelessHttpDefaults() {
        PlaywrightRuntimeMcpServer.ServerArgs args = PlaywrightRuntimeMcpServer.parseArgs(new String[]{
                "--transport", "streamable-http",
                "--host", "0.0.0.0",
                "--port", "9000",
                "--path", "/mcp",
                "--log-level", "DEBUG",
                "--no-banner"
        });

        assertEquals("streamable-http", args.transport());
        assertEquals("0.0.0.0", args.host());
        assertEquals(9000, args.port());
        assertEquals("/mcp", args.path());
        assertEquals("DEBUG", args.logLevel());
        assertTrue(args.noBanner());
        assertTrue(PlaywrightRuntimeMcpServer.resolveStatelessHttp(args, ""));
        assertFalse(PlaywrightRuntimeMcpServer.resolveStatelessHttp(args, "false"));
    }

    @Test
    void runtimeHealthReportsStoppedWhenRuntimeIsAbsent() {
        Map<String, Object> health = PlaywrightRuntimeMcpServer.browserRuntimeHealth();

        assertEquals(false, health.get("started"));
        assertTrue(health.containsKey("provider"));
        assertTrue(health.containsKey("model_name"));
    }

    private static final class FakeRuntime implements PlaywrightRuntimeMcpServer.RuntimeOperations {
        private String lastTask;
        private String lastSessionId;
        private String lastRequestId;

        @Override
        public void ensureStarted() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public Map<String, Object> runBrowserTask(String task, String sessionId, String requestId, Integer timeoutSeconds) {
            lastTask = task;
            lastSessionId = sessionId;
            lastRequestId = requestId;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            result.put("screenshot", "data:image/png;base64,AAAA");
            return result;
        }

        @Override
        public Map<String, Object> cancelRun(String sessionId, String requestId) {
            return Map.of("status", "cancelled", "session_id", sessionId, "request_id", requestId == null ? "" : requestId);
        }

        @Override
        public Map<String, Object> clearCancel(String sessionId, String requestId) {
            return Map.of("status", "cleared", "session_id", sessionId, "request_id", requestId == null ? "" : requestId);
        }

        @Override
        public Map<String, Object> runtimeHealth() {
            return Map.of("ok", true, "started", true);
        }
    }
}
