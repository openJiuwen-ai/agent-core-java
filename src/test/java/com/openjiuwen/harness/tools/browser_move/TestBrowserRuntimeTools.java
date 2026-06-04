/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserCancelTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserClearCancelTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserCustomActionTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserListActionsTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserRuntimeHealthTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserRuntimeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BrowserRuntimeTools.
 *
 * <p>Mirrors Python's {@code test_browser_runtime_tools.py} in
 * {@code tests/unit_tests/harness/tools/browser_move/test_browser_runtime_tools.py}.</p>
 */
@Tag("unit-test")
class TestBrowserRuntimeTools {

    @Test
    @DisplayName("buildBrowserRuntimeTools returns five helper tools")
    void testBuildBrowserRuntimeToolsReturnsFiveHelperToolsByDefault() {
        assertEquals(5, BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime()).size());
    }

    @Test
    @DisplayName("each helper is a Tool subclass")
    void testEachToolIsToolSubclass() {
        for (Tool tool : BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime())) {
            assertInstanceOf(Tool.class, tool);
        }
    }

    @Test
    @DisplayName("each helper has a ToolCard")
    void testEachToolHasToolCard() {
        for (Tool tool : BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime())) {
            assertInstanceOf(ToolCard.class, tool.getCard());
        }
    }

    @Test
    @DisplayName("default helper tool names match Python")
    void testDefaultHelperToolNames() {
        List<String> names = BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime()).stream()
                .map(tool -> tool.getCard().getName())
                .toList();

        assertEquals(List.of(
                "browser_cancel_run",
                "browser_clear_cancel",
                "browser_custom_action",
                "browser_list_custom_actions",
                "browser_runtime_health"
        ), names);
    }

    @Test
    @DisplayName("helper tool classes match Python")
    void testHelperToolClasses() {
        List<Tool> tools = BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime());

        assertInstanceOf(BrowserCancelTool.class, tools.get(0));
        assertInstanceOf(BrowserClearCancelTool.class, tools.get(1));
        assertInstanceOf(BrowserCustomActionTool.class, tools.get(2));
        assertInstanceOf(BrowserListActionsTool.class, tools.get(3));
        assertInstanceOf(BrowserRuntimeHealthTool.class, tools.get(4));
    }

    @Test
    @DisplayName("language en uses non-empty ASCII descriptions")
    void testLanguageEnUsesNonEmptyDescriptions() {
        for (Tool tool : BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "en")) {
            String description = tool.getCard().getDescription();
            assertNotNull(description);
            assertFalse(description.isBlank());
            assertTrue(description.chars().anyMatch(ch -> ch < 128 && Character.isAlphabetic(ch)));
        }
    }

    @Test
    @DisplayName("tool ids are non-empty")
    void testToolIdsAreNonEmpty() {
        for (Tool tool : BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime())) {
            assertNotNull(tool.getCard().getId());
            assertFalse(tool.getCard().getId().isBlank());
        }
    }

    @Test
    @DisplayName("cancel tool calls runtime cancelRun")
    void testCancelToolCallsCancelRun() throws Exception {
        CountingRuntime runtime = makeRuntime();
        BrowserCancelTool tool = new BrowserCancelTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("session_id", "s1"), Map.of());

        assertEquals(1, runtime.ensureRuntimeReadyCalls);
        assertEquals("s1", runtime.cancelSessionId);
        assertNull(runtime.cancelRequestId);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("clear cancel tool calls runtime clearCancel")
    void testClearCancelToolCallsRuntimeClearCancel() throws Exception {
        CountingRuntime runtime = makeRuntime();
        BrowserClearCancelTool tool = new BrowserClearCancelTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("session_id", "s1", "request_id", "r1"), Map.of());

        assertEquals(1, runtime.ensureRuntimeReadyCalls);
        assertEquals("s1", runtime.clearSessionId);
        assertEquals("r1", runtime.clearRequestId);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("list actions tool uses runtime API")
    void testListActionsToolUsesRuntimeApi() throws Exception {
        CountingRuntime runtime = makeRuntime();
        BrowserListActionsTool tool = new BrowserListActionsTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(), Map.of());

        assertEquals(1, runtime.listActionsCalls);
        assertTrue(result.isSuccess());
        assertEquals(List.of("echo"), ((Map<?, ?>) result.getData()).get("actions"));
    }

    @Test
    @DisplayName("custom action tool uses runtime API")
    void testCustomActionToolUsesRuntimeApi() throws Exception {
        CountingRuntime runtime = makeRuntime();
        BrowserCustomActionTool tool = new BrowserCustomActionTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "action", "echo",
                "session_id", "s1",
                "request_id", "r1",
                "params", Map.of("text", "hello")
        ), Map.of());

        assertEquals(1, runtime.ensureRuntimeReadyCalls);
        assertEquals("echo", runtime.customAction);
        assertEquals("s1", runtime.customSessionId);
        assertEquals("r1", runtime.customRequestId);
        assertEquals(Map.of("text", "hello"), runtime.customParams);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("runtime health tool uses runtime API")
    void testRuntimeHealthToolUsesRuntimeApi() throws Exception {
        CountingRuntime runtime = makeRuntime();
        BrowserRuntimeHealthTool tool = new BrowserRuntimeHealthTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(), Map.of());

        assertEquals(1, runtime.runtimeHealthCalls);
        assertFalse(result.isSuccess());
        assertEquals(Boolean.FALSE, ((Map<?, ?>) result.getData()).get("started"));
    }

    private static CountingRuntime makeRuntime() {
        McpServerConfig mcpCfg = McpServerConfig.builder()
                .serverId("test")
                .serverName("test")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", "."))
                .build();
        return new CountingRuntime(mcpCfg);
    }

    private static final class CountingRuntime extends BrowserAgentRuntime {
        private int ensureRuntimeReadyCalls;
        private int listActionsCalls;
        private int runtimeHealthCalls;
        private String cancelSessionId;
        private String cancelRequestId;
        private String clearSessionId;
        private String clearRequestId;
        private String customAction;
        private String customSessionId;
        private String customRequestId;
        private Map<String, Object> customParams;

        private CountingRuntime(McpServerConfig mcpCfg) {
            super("openai", "test-key", "https://example.invalid/v1", "test-model",
                    mcpCfg, new BrowserRunGuardrails(3, 1, 30, false));
        }

        @Override
        public void ensureRuntimeReady() {
            ensureRuntimeReadyCalls++;
        }

        @Override
        public Map<String, Object> cancelRun(String sessionId, String requestId) {
            cancelSessionId = sessionId;
            cancelRequestId = requestId;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            result.put("error", null);
            return result;
        }

        @Override
        public Map<String, Object> clearCancel(String sessionId, String requestId) {
            clearSessionId = sessionId;
            clearRequestId = requestId;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            result.put("error", null);
            return result;
        }

        @Override
        public Map<String, Object> listActions() {
            listActionsCalls++;
            return Map.of("ok", true, "actions", List.of("echo"), "details", Map.of("echo", Map.of()));
        }

        @Override
        public Map<String, Object> runCustomAction(String action, String sessionId, String requestId, Map<String, Object> params) {
            customAction = action;
            customSessionId = sessionId;
            customRequestId = requestId;
            customParams = params;
            return Map.of("ok", true, "session_id", sessionId, "request_id", requestId, "action", action);
        }

        @Override
        public Map<String, Object> runtimeHealth() {
            runtimeHealthCalls++;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("started", false);
            result.put("last_heartbeat_ok", null);
            result.put("provider", "openai");
            result.put("api_base", "https://example.invalid/v1");
            result.put("model_name", "test-model");
            return result;
        }
    }
}
