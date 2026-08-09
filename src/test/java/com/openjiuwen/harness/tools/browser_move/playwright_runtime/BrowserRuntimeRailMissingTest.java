/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for browser runtime rail lifecycle hooks.
 *
 * <p>Mirrors Python's {@code BrowserRuntimeRail} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.browser_move.test_browser_runtime_rail} in
 * {@code tests/unit_tests/harness/tools/browser_move/test_browser_runtime_rail.py}.</p>
 */
class BrowserRuntimeRailMissingTest {

    private static final String SOURCE =
            "tests/unit_tests/harness/tools/browser_move/test_browser_runtime_rail.py";
    private static final String PROGRESS_STATE_KEY = "__browser_subagent_progress_state__";
    private static final String PROGRESS_TASK_KEY = "__browser_subagent_last_task__";

    @TestFactory
    Collection<DynamicTest> pythonBrowserRuntimeRailCases() {
        return List.of(
                caseOf("test_rail_is_agent_rail_subclass",
                        BrowserRuntimeRailMissingTest::railIsAgentRailSubclass),
                caseOf("test_rail_holds_runtime_reference",
                        BrowserRuntimeRailMissingTest::railHoldsRuntimeReference),
                caseOf("test_before_invoke_calls_ensure_runtime_ready",
                        BrowserRuntimeRailMissingTest::beforeInvokeCallsEnsureRuntimeReady),
                caseOf("test_before_invoke_called_twice_delegates_twice",
                        BrowserRuntimeRailMissingTest::beforeInvokeCalledTwiceDelegatesTwice),
                caseOf("test_rail_registered_for_before_invoke_event",
                        BrowserRuntimeRailMissingTest::railRegisteredForBeforeInvokeEvent),
                caseOf("test_before_invoke_persists_current_query_for_continuation",
                        BrowserRuntimeRailMissingTest::beforeInvokePersistsCurrentQueryForContinuation),
                caseOf("test_before_model_call_injects_progress_sections",
                        BrowserRuntimeRailMissingTest::beforeModelCallInjectsProgressSections),
                caseOf("test_after_tool_call_records_browser_tool_progress",
                        BrowserRuntimeRailMissingTest::afterToolCallRecordsBrowserToolProgress),
                caseOf("test_after_invoke_rewrites_max_iteration_with_failure_summary",
                        BrowserRuntimeRailMissingTest::afterInvokeRewritesMaxIterationWithFailureSummary),
                caseOf("test_after_invoke_promotes_completed_progress_block",
                        BrowserRuntimeRailMissingTest::afterInvokePromotesCompletedProgressBlock)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void railIsAgentRailSubclass() {
        assertThat(new BrowserRuntimeRail(runtime())).isInstanceOf(DeepAgentRail.class);
    }

    private static void railHoldsRuntimeReference() {
        RecordingRuntime runtime = runtime();

        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);

        assertThat(rail.getRuntime()).isSameAs(runtime);
    }

    private static void beforeInvokeCallsEnsureRuntimeReady() {
        RecordingRuntime runtime = runtime();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);
        DeepAgent agent = new DeepAgent();
        FakeSession session = new FakeSession();

        rail.beforeInvoke(ctx(agent, session, Map.of()));

        assertThat(runtime.ensureRuntimeReadyCalls).isEqualTo(1);
        assertThat(agent.getAbilityManager().get("test-playwright")).isPresent();
    }

    private static void beforeInvokeCalledTwiceDelegatesTwice() {
        RecordingRuntime runtime = runtime();
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime);

        rail.beforeInvoke(ctx(new DeepAgent(), new FakeSession("session-1"), Map.of()));
        rail.beforeInvoke(ctx(new DeepAgent(), new FakeSession("session-2"), Map.of()));

        assertThat(runtime.ensureRuntimeReadyCalls).isEqualTo(2);
    }

    private static void railRegisteredForBeforeInvokeEvent() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());

        assertThat(rail.getCallbacks()).containsEntry("before_invoke", "beforeInvoke");
    }

    private static void beforeInvokePersistsCurrentQueryForContinuation() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();

        rail.beforeInvoke(ctx(new DeepAgent(), session, Map.of("query", "open example.com")));

        assertThat(session.getState(PROGRESS_TASK_KEY)).isEqualTo("open example.com");
    }

    private static void beforeModelCallInjectsProgressSections() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        session.updateState(Map.of(
                PROGRESS_STATE_KEY,
                Map.of(
                        "status", "partial",
                        "completed_steps", List.of("Opened home page"),
                        "remaining_steps", List.of("Submit the form"),
                        "next_step", "Fill the last required field",
                        "completion_evidence", List.of(),
                        "missing_requirements", List.of("Need the user email"),
                        "recent_tool_steps", List.of("browser_navigate: https://example.com"),
                        "last_page", Map.of("url", "https://example.com", "title", "Example"),
                        "last_screenshot", "",
                        "last_worker_final", "Waiting on the email field"
                )
        ));
        SystemPromptBuilder builder = new SystemPromptBuilder("en");

        rail.beforeModelCall(ctx(new DeepAgent(), session, Map.of("system_prompt_builder", builder)));

        String prompt = builder.build();
        assertThat(prompt).contains("Known progress for continuation");
        assertThat(prompt).contains("Opened home page");
        assertThat(prompt).contains("<browser_progress>{...}</browser_progress>");
    }

    @SuppressWarnings("unchecked")
    private static void afterToolCallRecordsBrowserToolProgress() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        Map<String, Object> toolData = Map.of(
                "page", Map.of("url", "https://example.com", "title", "Example")
        );

        rail.afterToolCall(ctx(new DeepAgent(), session, Map.of(
                "tool_name", "browser_navigate",
                "tool_result", ToolOutput.success(toolData)
        )));

        Map<String, Object> progress = (Map<String, Object>) session.getState(PROGRESS_STATE_KEY);
        assertThat(progress.get("status")).isEqualTo("partial");
        assertThat((Map<String, Object>) progress.get("last_page"))
                .containsEntry("url", "https://example.com")
                .containsEntry("title", "Example");
        assertThat((List<String>) progress.get("recent_tool_steps"))
                .anySatisfy(step -> assertThat(step).startsWith("browser_navigate:"));
    }

    private static void afterInvokeRewritesMaxIterationWithFailureSummary() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        session.updateState(Map.of(
                PROGRESS_TASK_KEY, "Finish the checkout flow",
                PROGRESS_STATE_KEY, Map.of(
                        "status", "partial",
                        "completed_steps", List.of("Opened checkout"),
                        "remaining_steps", List.of("Submit order"),
                        "last_page", Map.of("url", "https://example.com", "title", "Example")
                )
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", BrowserService.MAX_ITERATION_MESSAGE);
        result.put("result_type", "error");

        rail.afterInvoke(ctx(new DeepAgent(), session, Map.of("result", result)));

        assertThat(result.get("output")).asString().startsWith("Failure summary for continuation:");
        assertThat(result.get("failure_summary")).asString().startsWith("Failure summary for continuation:");
        assertThat(result.get("progress_state")).isInstanceOf(Map.class);
    }

    private static void afterInvokePromotesCompletedProgressBlock() {
        BrowserRuntimeRail rail = new BrowserRuntimeRail(runtime());
        FakeSession session = new FakeSession();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", "Settings saved successfully.\n"
                + "<browser_progress>{\"status\":\"completed\","
                + "\"completed_steps\":[\"Opened settings\"],"
                + "\"completion_evidence\":[\"Saved the settings page\"],"
                + "\"missing_requirements\":[]}</browser_progress>");
        result.put("result_type", "error");

        rail.afterInvoke(ctx(new DeepAgent(), session, Map.of("result", result)));

        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(result.get("output")).isEqualTo("Settings saved successfully.");
        assertThat(session.getState(PROGRESS_STATE_KEY)).isEqualTo(Map.of());
    }

    private static CallbackContext ctx(DeepAgent agent, FakeSession session, Map<String, Object> values) {
        Map<String, Object> contextValues = new LinkedHashMap<>();
        if (values != null) {
            contextValues.putAll(values);
        }
        contextValues.putIfAbsent("session", session);
        return new CallbackContext(agent, contextValues);
    }

    private static RecordingRuntime runtime() {
        return new RecordingRuntime();
    }

    private static final class RecordingRuntime extends BrowserAgentRuntime {
        private int ensureRuntimeReadyCalls;

        private RecordingRuntime() {
            super(
                    "openai",
                    "test-key",
                    "https://example.invalid/v1",
                    "test-model",
                    McpServerConfig.builder()
                            .serverId("test-playwright")
                            .serverName("test-playwright")
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
    }

    private static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeSession() {
            this("browser-session");
        }

        private FakeSession(String sessionId) {
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
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
