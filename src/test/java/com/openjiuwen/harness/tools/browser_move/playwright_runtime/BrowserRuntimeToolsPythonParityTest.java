/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supplemental parity tests for browser runtime helper tools.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/browser_move/test_browser_runtime_tools.py}.</p>
 */
class BrowserRuntimeToolsPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/browser_move/test_browser_runtime_tools.py";

    @TestFactory
    Collection<DynamicTest> pythonBrowserRuntimeToolCases() {
        return List.of(
                caseOf("test_build_browser_runtime_tools_returns_five_helper_tools_by_default",
                        BrowserRuntimeToolsPythonParityTest::buildBrowserRuntimeToolsReturnsSevenHelperToolsByDefault),
                caseOf("test_each_tool_is_tool_subclass",
                        BrowserRuntimeToolsPythonParityTest::eachToolIsToolSubclass),
                caseOf("test_each_tool_has_tool_card",
                        BrowserRuntimeToolsPythonParityTest::eachToolHasToolCard),
                caseOf("test_default_helper_tool_names",
                        BrowserRuntimeToolsPythonParityTest::defaultHelperToolNames),
                caseOf("test_helper_tool_classes",
                        BrowserRuntimeToolsPythonParityTest::helperToolClasses),
                caseOf("test_language_en_uses_non_empty_descriptions",
                        BrowserRuntimeToolsPythonParityTest::languageEnUsesNonEmptyDescriptions),
                caseOf("test_tool_ids_are_non_empty",
                        BrowserRuntimeToolsPythonParityTest::toolIdsAreNonEmpty),
                caseOf("test_cancel_tool_calls_cancel_run",
                        BrowserRuntimeToolsPythonParityTest::cancelToolCallsCancelRun),
                caseOf("test_clear_cancel_tool_calls_runtime_clear_cancel",
                        BrowserRuntimeToolsPythonParityTest::clearCancelToolCallsRuntimeClearCancel),
                caseOf("test_list_actions_tool_uses_runtime_api",
                        BrowserRuntimeToolsPythonParityTest::listActionsToolUsesRuntimeApi),
                caseOf("test_custom_action_tool_uses_runtime_api",
                        BrowserRuntimeToolsPythonParityTest::customActionToolUsesRuntimeApi),
                caseOf("test_probe_interactives_tool_uses_runtime_api",
                        BrowserRuntimeToolsPythonParityTest::probeInteractivesToolUsesRuntimeApi),
                caseOf("test_probe_cards_tool_uses_runtime_api",
                        BrowserRuntimeToolsPythonParityTest::probeCardsToolUsesRuntimeApi),
                caseOf("test_runtime_health_tool_uses_runtime_api",
                        BrowserRuntimeToolsPythonParityTest::runtimeHealthToolUsesRuntimeApi)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void buildBrowserRuntimeToolsReturnsSevenHelperToolsByDefault() {
        assertThat(BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "cn")).hasSize(7);
    }

    private static void eachToolIsToolSubclass() {
        assertThat(BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "cn"))
                .allSatisfy(tool -> assertThat(tool).isInstanceOf(Tool.class));
    }

    private static void eachToolHasToolCard() {
        assertThat(BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "cn"))
                .allSatisfy(tool -> assertThat(tool.getCard()).isInstanceOf(ToolCard.class));
    }

    private static void defaultHelperToolNames() {
        List<String> names = BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "cn").stream()
                .map(tool -> tool.getCard().getName())
                .toList();

        assertThat(names).containsExactly(
                "browser_cancel_run",
                "browser_clear_cancel",
                "browser_custom_action",
                "browser_list_custom_actions",
                "browser_probe_interactives",
                "browser_probe_cards",
                "browser_runtime_health"
        );
    }

    private static void helperToolClasses() {
        List<Tool> tools = BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "cn");

        assertThat(tools.get(0)).isInstanceOf(BrowserRuntimeTools.BrowserCancelTool.class);
        assertThat(tools.get(1)).isInstanceOf(BrowserRuntimeTools.BrowserClearCancelTool.class);
        assertThat(tools.get(2)).isInstanceOf(BrowserRuntimeTools.BrowserCustomActionTool.class);
        assertThat(tools.get(3)).isInstanceOf(BrowserRuntimeTools.BrowserListActionsTool.class);
        assertThat(tools.get(4)).isInstanceOf(BrowserRuntimeTools.BrowserProbeInteractivesTool.class);
        assertThat(tools.get(5)).isInstanceOf(BrowserRuntimeTools.BrowserProbeCardsTool.class);
        assertThat(tools.get(6)).isInstanceOf(BrowserRuntimeTools.BrowserRuntimeHealthTool.class);
    }

    private static void languageEnUsesNonEmptyDescriptions() {
        assertThat(BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "en"))
                .allSatisfy(tool -> {
                    String description = tool.getCard().getDescription();
                    assertFalse(description.isBlank());
                    assertTrue(description.chars().anyMatch(ch -> ch < 128 && Character.isAlphabetic(ch)));
                });
    }

    private static void toolIdsAreNonEmpty() {
        assertThat(BrowserRuntimeTools.buildBrowserRuntimeTools(makeRuntime(), "cn"))
                .allSatisfy(tool -> assertThat(tool.getCard().getId()).isNotBlank());
    }

    private static void cancelToolCallsCancelRun() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserCancelTool tool = new BrowserRuntimeTools.BrowserCancelTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("session_id", "s1"), Map.of());

        assertThat(runtime.ensureRuntimeReadyCalls).isEqualTo(1);
        assertThat(runtime.cancelRunCalls).isEqualTo(1);
        assertThat(runtime.sessionId).isEqualTo("s1");
        assertThat(runtime.requestId).isNull();
        assertThat(result.isSuccess()).isTrue();
    }

    private static void clearCancelToolCallsRuntimeClearCancel() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserClearCancelTool tool = new BrowserRuntimeTools.BrowserClearCancelTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("session_id", "s1", "request_id", "r1"), Map.of());

        assertThat(runtime.ensureRuntimeReadyCalls).isEqualTo(1);
        assertThat(runtime.clearCancelCalls).isEqualTo(1);
        assertThat(runtime.sessionId).isEqualTo("s1");
        assertThat(runtime.requestId).isEqualTo("r1");
        assertThat(result.isSuccess()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static void listActionsToolUsesRuntimeApi() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserListActionsTool tool = new BrowserRuntimeTools.BrowserListActionsTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(), Map.of());

        assertThat(runtime.listActionsCalls).isEqualTo(1);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Map<String, Object>) result.getData()).get("actions")).isEqualTo(List.of("echo"));
    }

    private static void customActionToolUsesRuntimeApi() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserCustomActionTool tool = new BrowserRuntimeTools.BrowserCustomActionTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "action", "echo",
                "session_id", "s1",
                "request_id", "r1",
                "params", Map.of("text", "hello")
        ), Map.of());

        assertThat(runtime.runCustomActionCalls).isEqualTo(1);
        assertThat(runtime.action).isEqualTo("echo");
        assertThat(runtime.sessionId).isEqualTo("s1");
        assertThat(runtime.requestId).isEqualTo("r1");
        assertThat(runtime.params).isEqualTo(Map.of("text", "hello"));
        assertThat(result.isSuccess()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static void probeInteractivesToolUsesRuntimeApi() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserProbeInteractivesTool tool =
                new BrowserRuntimeTools.BrowserProbeInteractivesTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "max_items", 20,
                "viewport_only", true,
                "query", "next"
        ), Map.of());

        assertThat(runtime.probeInteractivesCalls).isEqualTo(1);
        assertThat(runtime.maxItems).isEqualTo(20);
        assertThat(runtime.viewportOnly).isTrue();
        assertThat(runtime.query).isEqualTo("next");
        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> element = (Map<String, Object>) ((List<?>) data.get("elements")).getFirst();
        assertThat(element.get("text")).isEqualTo("Next");
    }

    @SuppressWarnings("unchecked")
    private static void probeCardsToolUsesRuntimeApi() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserProbeCardsTool tool = new BrowserRuntimeTools.BrowserProbeCardsTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "max_cards", 20,
                "viewport_only", true,
                "include_buttons", true,
                "query", "book"
        ), Map.of());

        assertThat(runtime.probeCardsCalls).isEqualTo(1);
        assertThat(runtime.maxCards).isEqualTo(20);
        assertThat(runtime.viewportOnly).isTrue();
        assertThat(runtime.includeButtons).isTrue();
        assertThat(runtime.query).isEqualTo("book");
        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> card = (Map<String, Object>) ((List<?>) data.get("cards")).getFirst();
        assertThat(card.get("title")).isEqualTo("Book");
    }

    @SuppressWarnings("unchecked")
    private static void runtimeHealthToolUsesRuntimeApi() throws Exception {
        RecordingRuntime runtime = makeRuntime();
        BrowserRuntimeTools.BrowserRuntimeHealthTool tool =
                new BrowserRuntimeTools.BrowserRuntimeHealthTool(runtime);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(), Map.of());

        assertThat(runtime.runtimeHealthCalls).isEqualTo(1);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Map<String, Object>) result.getData()).get("started")).isEqualTo(false);
    }

    private static RecordingRuntime makeRuntime() {
        return new RecordingRuntime();
    }

    /**
     * Records calls made by browser runtime tools without starting Playwright.
     */
    private static final class RecordingRuntime extends BrowserAgentRuntime {
        private int ensureRuntimeReadyCalls;
        private int cancelRunCalls;
        private int clearCancelCalls;
        private int listActionsCalls;
        private int runCustomActionCalls;
        private int probeInteractivesCalls;
        private int probeCardsCalls;
        private int runtimeHealthCalls;
        private String action;
        private String sessionId;
        private String requestId;
        private Map<String, Object> params;
        private int maxItems;
        private int maxCards;
        private boolean viewportOnly;
        private boolean includeButtons;
        private String query;

        private RecordingRuntime() {
            super(
                    "openai",
                    "test-key",
                    "https://example.invalid/v1",
                    "test-model",
                    McpServerConfig.builder()
                            .serverId("test")
                            .serverName("test")
                            .serverPath("stdio://playwright")
                            .clientType("stdio")
                            .params(Map.of("cwd", "."))
                            .build(),
                    new BrowserRunGuardrails(3, 1, 30, false, false)
            );
        }

        @Override
        public void ensureRuntimeReady() {
            ensureRuntimeReadyCalls++;
        }

        @Override
        public Map<String, Object> cancelRun(String sessionId, String requestId) {
            cancelRunCalls++;
            this.sessionId = sessionId;
            this.requestId = requestId;
            return Map.of("ok", true, "session_id", sessionId, "error", "");
        }

        @Override
        public Map<String, Object> clearCancel(String sessionId, String requestId) {
            clearCancelCalls++;
            this.sessionId = sessionId;
            this.requestId = requestId;
            return Map.of("ok", true, "session_id", sessionId, "request_id", requestId, "error", "");
        }

        @Override
        public Map<String, Object> listActions() {
            listActionsCalls++;
            return Map.of("ok", true, "actions", List.of("echo"), "details", Map.of("echo", Map.of()));
        }

        @Override
        public Map<String, Object> runCustomAction(
                String action,
                String sessionId,
                String requestId,
                Map<String, Object> params
        ) {
            runCustomActionCalls++;
            this.action = action;
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.params = params;
            return Map.of("ok", true, "session_id", sessionId);
        }

        @Override
        public Map<String, Object> probeInteractives(int maxItems, boolean viewportOnly, String query) {
            probeInteractivesCalls++;
            this.maxItems = maxItems;
            this.viewportOnly = viewportOnly;
            this.query = query;
            return Map.of(
                    "ok", true,
                    "elements", List.of(Map.of(
                            "id", "e1",
                            "role", "button",
                            "text", "Next",
                            "selector_hint", "button:nth-of-type(1)"
                    )),
                    "error", ""
            );
        }

        @Override
        public Map<String, Object> probeCards(int maxCards, boolean viewportOnly, boolean includeButtons, String query) {
            probeCardsCalls++;
            this.maxCards = maxCards;
            this.viewportOnly = viewportOnly;
            this.includeButtons = includeButtons;
            this.query = query;
            return Map.of(
                    "ok", true,
                    "cards", List.of(Map.of(
                            "id", "card_1",
                            "title", "Book",
                            "price", "10.00",
                            "selector_hint", "article.product_pod"
                    )),
                    "error", ""
            );
        }

        @Override
        public Map<String, Object> runtimeHealth() {
            runtimeHealthCalls++;
            return Map.of(
                    "ok", false,
                    "started", false,
                    "last_heartbeat_ok", "",
                    "provider", "openai",
                    "api_base", "https://example.invalid/v1",
                    "model_name", "test-model"
            );
        }
    }
}
