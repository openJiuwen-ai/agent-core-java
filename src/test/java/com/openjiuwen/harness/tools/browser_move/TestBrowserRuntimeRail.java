/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BrowserRuntimeRail lifecycle hook.
 *
 * <p>Mirrors Python's {@code test_browser_runtime_rail.py} in
 * {@code tests.unit_tests.harness.tools.browser_move}.</p>
 */
class TestBrowserRuntimeRail {

    @Test
    @Tag("level0")
    @DisplayName("BrowserRuntimeRail is an agent rail subclass")
    void testRailIsAgentRailSubclass() {
        assertTrue(AgentRail.class.isAssignableFrom(BrowserRuntimeRail.class));
        assertTrue(DeepAgentRail.class.isAssignableFrom(BrowserRuntimeRail.class));
    }

    @Test
    @Tag("level0")
    @DisplayName("BrowserRuntimeRail holds runtime reference")
    void testRailHoldsRuntimeReference() {
        CountingRuntime runtime = runtime();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);

        assertSame(runtime, rail.getRuntime());
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeInvoke prepares runtime and registers MCP ability")
    void testBeforeInvokeCallsEnsureRuntimeReady() {
        CountingRuntime runtime = runtime();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);
        FakeAgent agent = new FakeAgent();
        FakeSession session = new FakeSession();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .session(session)
                .inputs(InvokeInputs.builder()
                        .query("open example.com")
                        .conversationId(session.getSessionId())
                        .build())
                .build();

        rail.beforeInvoke(ctx);

        assertEquals(1, runtime.ensureRuntimeReadyCalls);
        assertTrue(runtime.isRuntimeReady());
        assertSame(runtime.getService().getMcpCfg(), agent.abilityManager.added);
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeInvoke delegates readiness on every call")
    void testBeforeInvokeCalledTwiceDelegatesTwice() {
        CountingRuntime runtime = runtime();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);

        rail.beforeInvoke(context(new FakeAgent(), new FakeSession(), "first"));
        rail.beforeInvoke(context(new FakeAgent(), new FakeSession(), "second"));

        assertEquals(2, runtime.ensureRuntimeReadyCalls);
    }

    @Test
    @Tag("level0")
    @DisplayName("getCallbacks includes beforeInvoke")
    void testRailRegisteredForBeforeInvokeEvent() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());

        assertTrue(rail.getCallbacks().containsKey(AgentCallbackEvent.BEFORE_INVOKE));
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeInvoke persists current query for continuation")
    void testBeforeInvokePersistsCurrentQueryForContinuation() {
        FakeSession session = new FakeSession();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());

        rail.beforeInvoke(context(new FakeAgent(), session, "open example.com"));

        assertEquals("open example.com", session.getState("__browser_subagent_last_task__"));
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeModelCall injects progress continuation sections")
    void testBeforeModelCallInjectsProgressSections() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        session.updateState(Map.of(
                "__browser_subagent_progress_state__",
                Map.of(
                        "status", "partial",
                        "completed_steps", List.of("Opened home page"),
                        "remaining_steps", List.of("Submit the form"),
                        "next_step", "Fill the last required field",
                        "completion_evidence", List.of(),
                        "missing_requirements", List.of("Need the user email"),
                        "recent_tool_steps", List.of("browser_navigate: https://example.com"),
                        "last_page", Map.of("url", "https://example.com", "title", "Example")
                )
        ));
        FakeAgent agent = new FakeAgent();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .session(session)
                .inputs(InvokeInputs.builder().query("continue").build())
                .build();

        rail.beforeModelCall(ctx);

        String prompt = agent.systemPromptBuilder.build();
        assertTrue(prompt.contains("Known progress for continuation"));
        assertTrue(prompt.contains("Opened home page"));
        assertTrue(prompt.contains("<browser_progress>{...}</browser_progress>"));
    }

    @Test
    @Tag("level0")
    @DisplayName("afterToolCall records browser tool progress")
    void testAfterToolCallRecordsBrowserToolProgress() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(new FakeAgent())
                .session(session)
                .inputs(ToolCallInputs.builder()
                        .toolName("browser_navigate")
                        .toolResult(new ToolOutput(
                                true,
                                Map.of("page", Map.of("url", "https://example.com", "title", "Example")),
                                null))
                        .build())
                .build();

        rail.afterToolCall(ctx);

        Map<?, ?> progress = (Map<?, ?>) session.getState("__browser_subagent_progress_state__");
        assertNotNull(progress);
        assertFalse(progress.isEmpty());
        assertEquals("partial", progress.get("status"));
    }

    @Test
    @Tag("level0")
    @DisplayName("afterInvoke rewrites max iteration result with failure summary")
    void testAfterInvokeRewritesMaxIterationWithFailureSummary() {
        CountingRuntime runtime = runtime();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);
        FakeSession session = new FakeSession();
        session.updateState(Map.of(
                "__browser_subagent_last_task__", "Finish the checkout flow",
                "__browser_subagent_progress_state__", Map.of(
                        "status", "partial",
                        "completed_steps", List.of("Opened checkout"),
                        "next_step", "Submit payment"
                )
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", BrowserService.MAX_ITERATION_MESSAGE);
        result.put("result_type", "error");
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(new FakeAgent())
                .session(session)
                .inputs(InvokeInputs.builder().query("Finish checkout").result(result).build())
                .build();

        rail.afterInvoke(ctx);

        assertTrue(String.valueOf(result.get("output")).startsWith("Failure summary for continuation:"));
        assertTrue(String.valueOf(result.get("failure_summary")).startsWith("Failure summary for continuation:"));
        assertEquals("partial", ((Map<?, ?>) result.get("progress_state")).get("status"));
    }

    @Test
    @Tag("level0")
    @DisplayName("afterInvoke promotes completed browser progress block")
    void testAfterInvokePromotesCompletedProgressBlock() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "output",
                "Settings saved successfully.\n"
                        + "<browser_progress>{\"status\":\"completed\","
                        + "\"completed_steps\":[\"Opened settings\"],"
                        + "\"completion_evidence\":[\"Saved the settings page\"]}</browser_progress>");
        result.put("result_type", "error");
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(new FakeAgent())
                .session(session)
                .inputs(InvokeInputs.builder().query("Save settings").result(result).build())
                .build();

        rail.afterInvoke(ctx);

        assertEquals("answer", result.get("result_type"));
        assertEquals("Settings saved successfully.", result.get("output"));
        assertEquals(Map.of(), session.getState("__browser_subagent_progress_state__"));
    }

    private static AgentCallbackContext context(FakeAgent agent, FakeSession session, String query) {
        return AgentCallbackContext.builder()
                .agent(agent)
                .session(session)
                .inputs(InvokeInputs.builder().query(query).conversationId(session.getSessionId()).build())
                .build();
    }

    private static CountingRuntime runtime() {
        return new CountingRuntime(McpServerConfig.builder()
                .serverId("playwright-runtime-wrapper")
                .serverName("playwright-runtime-wrapper")
                .serverPath("stdio://playwright-runtime-wrapper")
                .clientType("stdio")
                .params(new LinkedHashMap<>())
                .build());
    }

    static final class CountingRuntime extends BrowserAgentRuntime {
        int ensureRuntimeReadyCalls;

        CountingRuntime(McpServerConfig mcpConfig) {
            super("openai", "", "https://api.openai.com/v1", "test-model", mcpConfig, new BrowserRunGuardrails());
        }

        @Override
        public void ensureRuntimeReady() {
            ensureRuntimeReadyCalls++;
            super.ensureRuntimeReady();
        }
    }

    static final class FakeAgent {
        public final AbilityManagerStub abilityManager = new AbilityManagerStub();
        public final SystemPromptBuilder systemPromptBuilder = new SystemPromptBuilder("en");
    }

    static final class AbilityManagerStub {
        Object added;

        public void add(Object ability) {
            this.added = ability;
        }
    }

    static final class FakeSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        FakeSession() {
            this("browser-session");
        }

        FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }
}
