/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Tool Span creation via {@link ObservabilityRail}.
 *
 * <p>Translates the Python test
 * {@code test_tool_call_nests_under_agent_span} and related tool-span
 * scenarios from {@code test_observability.py} into JUnit 5.</p>
 *
 * <p>Verifies that tool spans are created as children of the agent span,
 * carry the correct semantic-convention attributes
 * ({@code gen_ai.tool.name}, {@code gen_ai.operation.name=execute_tool}),
 * and handle exceptions correctly (ERROR status, double-ending prevention).</p>
 *
 * <p>Span tree exercised:</p>
 * <pre>
 * team.{name}                        [startTeamTrace]
 * └── agent.{member}.invoke          [beforeInvoke / afterInvoke]
 *     └── tool.{toolName}            [beforeToolCall / afterToolCall]
 * </pre>
 *
 * @since 0.1.7
 */
@DisplayName("Tool Span tests via ObservabilityRail")
class ToolSpanTest extends ObservabilityTestBase {

    // ================================================================
    // Tool span nesting under agent span
    // ================================================================

    @Test
    @DisplayName("tool span nests under agent span")
    void test_tool_call_nests_under_agent_span() {
        // 1. Create team span (simulates Runner._maybe_attach_observability).
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        // 2. Create agent and invoke context.
        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "use the calc tool");

        // 3. Rail creates agent span via beforeInvoke.
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // 4. Tool call within the agent invoke.
        ToolCallInputs toolInputs = toolCallInputs("calc", "calc-1", Map.of("expr", "6*7"));
        ctx.setInputs(toolInputs);
        rail.beforeToolCall(ctx);

        // 5. Close the tool span.
        rail.afterToolCall(ctx);

        // 6. Restore invoke inputs (with result) and close agent span.
        ctx.setInputs(invokeInputsWithResult("use the calc tool", simpleResult("42")));
        rail.afterInvoke(ctx);

        // 7. Close the team span.
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // --- Verify tool span ---
        List<SpanData> toolSpans = spansByName("tool.calc");
        assertThat(toolSpans).as("tool span missing").isNotEmpty();
        SpanData toolSpan = toolSpans.get(0);

        // --- Verify agent span ---
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans).as("agent span missing").isNotEmpty();
        SpanData agentSpan = agentSpans.get(0);

        // --- Verify team span ---
        List<SpanData> teamSpans = spansByName("team.test_team");
        assertThat(teamSpans).as("team span missing").isNotEmpty();
        SpanData teamSpan = teamSpans.get(0);

        // Tool span is child of agent span.
        assertThat(toolSpan.getParentSpanId())
                .as("tool span parent should be agent span")
                .isEqualTo(agentSpan.getSpanId());

        // Agent span is child of team span.
        assertThat(agentSpan.getParentSpanId())
                .as("agent span parent should be team span")
                .isEqualTo(teamSpan.getSpanId());
    }

    // ================================================================
    // Tool span attributes
    // ================================================================

    @Test
    @DisplayName("tool span has gen_ai.tool.name and gen_ai.operation.name=execute_tool")
    void test_tool_span_has_correct_attributes() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "run tool");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ToolCallInputs toolInputs = toolCallInputs("search", "search-1",
                Map.of("query", "hello"));
        ctx.setInputs(toolInputs);
        rail.beforeToolCall(ctx);

        // Provide a result for the after-tool-call.
        ctx.setInputs(toolCallInputs("search", "search-1",
                Map.of("query", "hello"), "search results"));
        rail.afterToolCall(ctx);

        ctx.setInputs(invokeInputsWithResult("run tool", simpleResult("done")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData toolSpan = spansByName("tool.search").get(0);

        // Span name is tool.{toolName}.
        assertThat(toolSpan.getName()).isEqualTo("tool.search");

        // gen_ai.tool.name attribute.
        assertThat(attr(toolSpan, ObservabilitySemConv.GEN_AI_TOOL_NAME))
                .isEqualTo("search");

        // gen_ai.operation.name = execute_tool.
        assertThat(attr(toolSpan, ObservabilitySemConv.GEN_AI_OPERATION_NAME))
                .isEqualTo("execute_tool");

        // langfuse.observation.type = tool.
        assertThat(attr(toolSpan, ObservabilitySemConv.LANGFUSE_OBSERVATION_TYPE))
                .isEqualTo("tool");

        // gen_ai.tool.id should be present (from ToolCall.getId()).
        assertThat(attr(toolSpan, ObservabilitySemConv.GEN_AI_TOOL_ID))
                .isEqualTo("search-1");

        // Tool input should be recorded.
        assertThat(hasAttr(toolSpan, ObservabilitySemConv.GEN_AI_TOOL_INPUT))
                .as("tool input attribute should be present")
                .isTrue();

        // Tool output should be recorded (afterToolCall sets it).
        assertThat(hasAttr(toolSpan, ObservabilitySemConv.GEN_AI_TOOL_OUTPUT))
                .as("tool output attribute should be present")
                .isTrue();
    }

    @Test
    @DisplayName("tool span has OK status on normal completion")
    void test_tool_span_ok_status_on_normal_completion() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "run tool");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ToolCallInputs toolInputs = toolCallInputs("calc", "calc-1",
                Map.of("expr", "1+1"), 2);
        ctx.setInputs(toolInputs);
        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);

        ctx.setInputs(invokeInputsWithResult("run tool", simpleResult("2")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData toolSpan = spansByName("tool.calc").get(0);
        assertThat(toolSpan.getStatus().getStatusCode())
                .as("tool span should have OK status on normal completion")
                .isEqualTo(StatusCode.OK);
    }

    // ================================================================
    // Error handling: tool exception creates ERROR status span
    // ================================================================

    @Test
    @DisplayName("tool exception creates ERROR status span via onToolException")
    void test_tool_exception_creates_error_status() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "use failing tool");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Open tool span.
        ToolCallInputs toolInputs = toolCallInputs("failing_tool", "ft-1",
                Map.of("expr", "bad"));
        ctx.setInputs(toolInputs);
        rail.beforeToolCall(ctx);

        // Simulate tool exception: set exception and call onToolException.
        ctx.setException(new RuntimeException("tool exploded"));
        rail.onToolException(ctx);

        // afterToolCall should NOT be called in the normal error flow,
        // but we call it here to verify it is safely skipped.
        rail.afterToolCall(ctx);

        // Close agent span (exception still set → agent span gets ERROR too).
        ctx.setInputs(invokeInputsWithResult("use failing tool", null));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // Verify tool span has ERROR status.
        List<SpanData> toolSpans = spansByName("tool.failing_tool");
        assertThat(toolSpans).as("tool span should exist after exception").isNotEmpty();
        SpanData toolSpan = toolSpans.get(0);
        assertThat(toolSpan.getStatus().getStatusCode())
                .as("tool span should have ERROR status after exception")
                .isEqualTo(StatusCode.ERROR);

        // The exception should be recorded as an event.
        assertThat(toolSpan.getEvents())
                .as("exception event should be recorded on tool span")
                .isNotEmpty();
    }

    // ================================================================
    // Double-ending prevention: afterToolCall skips when exception handled
    // ================================================================

    @Test
    @DisplayName("afterToolCall skips when exception already handled by onToolException")
    void test_after_tool_call_skips_when_exception_handled() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "double end test");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Open tool span.
        ToolCallInputs toolInputs = toolCallInputs("double_end_tool", "det-1", null);
        ctx.setInputs(toolInputs);
        rail.beforeToolCall(ctx);

        // Exception path: onToolException ends the span with ERROR.
        ctx.setException(new IllegalStateException("boom"));
        rail.onToolException(ctx);

        // Now call afterToolCall — it should skip because exception is set.
        // This must NOT create a second span, overwrite status to OK,
        // or throw.
        rail.afterToolCall(ctx);

        // Close agent span.
        ctx.setInputs(invokeInputsWithResult("double end test", null));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // Exactly 1 tool span (not 2 — no double creation).
        List<SpanData> toolSpans = spansByName("tool.double_end_tool");
        assertThat(toolSpans)
                .as("should have exactly 1 tool span (no double-end)")
                .hasSize(1);

        // Status is ERROR (set by onToolException), NOT OK.
        SpanData toolSpan = toolSpans.get(0);
        assertThat(toolSpan.getStatus().getStatusCode())
                .as("tool span status should remain ERROR (not overwritten to OK)")
                .isEqualTo(StatusCode.ERROR);
    }

    // ================================================================
    // No orphan tool spans
    // ================================================================

    @Test
    @DisplayName("no orphan tool spans — all tool spans have valid parents")
    void test_no_orphan_tool_spans() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "multi tool");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Tool 1.
        ToolCallInputs tool1 = toolCallInputs("tool_a", "ta-1", null, "result_a");
        ctx.setInputs(tool1);
        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);

        // Tool 2.
        ToolCallInputs tool2 = toolCallInputs("tool_b", "tb-1", null, "result_b");
        ctx.setInputs(tool2);
        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);

        ctx.setInputs(invokeInputsWithResult("multi tool", simpleResult("done")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // Collect all span IDs.
        List<SpanData> all = finishedSpans();
        java.util.Set<String> spanIds = new java.util.HashSet<>();
        for (SpanData s : all) {
            spanIds.add(s.getSpanId());
        }

        // Every non-root span's parent must exist in the set.
        for (SpanData s : all) {
            if (s.getParentSpanId() != null
                    && !s.getParentSpanId().equals(io.opentelemetry.api.trace.SpanId.getInvalid())) {
                assertThat(spanIds)
                        .as("orphan span: %s parent %s not found", s.getName(), s.getParentSpanId())
                        .contains(s.getParentSpanId());
            }
        }

        // Both tool spans exist and are children of the agent span.
        SpanData agentSpan = spansByPrefix("agent.leader.invoke").get(0);
        SpanData toolA = spansByName("tool.tool_a").get(0);
        SpanData toolB = spansByName("tool.tool_b").get(0);

        assertThat(toolA.getParentSpanId()).isEqualTo(agentSpan.getSpanId());
        assertThat(toolB.getParentSpanId()).isEqualTo(agentSpan.getSpanId());
    }
}
