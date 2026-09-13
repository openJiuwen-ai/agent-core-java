/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservabilityRail} agent iteration spans, error handling,
 * trace isolation, team span survival, and full span tree shape.
 *
 * <p>Translates the following Python tests from
 * {@code test_observability.py} into JUnit 5:</p>
 * <ul>
 *   <li>{@code test_observability_rail_opens_and_closes_iteration_span}</li>
 *   <li>{@code test_observability_rail_marks_error_on_exception}</li>
 *   <li>{@code test_two_runs_produce_two_separate_traces}</li>
 *   <li>{@code test_team_span_survives_after_rail_iteration}</li>
 *   <li>{@code test_span_tree_shape}</li>
 * </ul>
 *
 * <p>Key adaptations from Python to Java:</p>
 * <ul>
 *   <li>Python {@code before_task_iteration}/{@code after_task_iteration}
 *       mapped to Java {@code beforeInvoke}/{@code afterInvoke}.</li>
 *   <li>Python {@code TaskIterationInputs} mapped to Java
 *       {@link InvokeInputs}.</li>
 *   <li>Python span name {@code agent.{member}.task_iteration.{n}} mapped to
 *       Java span name {@code agent.{member}.invoke}.</li>
 *   <li>Python {@code get_team_span(name)}/{@code remove_team_span(name)}
 *       mapped to Java {@link OtelSpanContext#getTeamSpan()} /
 *       {@link ObservabilitySetup#finalizeTeamTrace(String)}.</li>
 *   <li>Python callback-framework LLM/tool triggers mapped to direct
 *       Rail hook calls ({@code beforeModelCall}/{@code afterModelCall} /
 *       {@code beforeToolCall}/{@code afterToolCall}).</li>
 * </ul>
 *
 * <p>Span tree exercised:</p>
 * <pre>
 * team.{name}                        [startTeamTrace / finalizeTeamTrace]
 * └── agent.{member}.invoke          [beforeInvoke / afterInvoke]
 *     ├── llm.call                   [beforeModelCall / afterModelCall]
 *     └── tool.{toolName}            [beforeToolCall / afterToolCall]
 * </pre>
 *
 * @since 0.1.7
 */
@DisplayName("ObservabilityRail tests: iteration spans, error handling, trace isolation, tree shape")
class ObservabilityRailTest extends ObservabilityTestBase {

    // ================================================================
    // Rail opens and closes agent iteration span
    // Mirrors: test_observability_rail_opens_and_closes_iteration_span
    // ================================================================

    @Test
    @DisplayName("rail opens and closes agent.invoke span with correct attributes")
    void test_observability_rail_opens_and_closes_iteration_span() {
        // 1. Create team span (simulates Runner._maybe_attach_observability).
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        // 2. Create agent and invoke context.
        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");

        // 3. Rail creates agent span via beforeInvoke.
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // 4. Restore invoke inputs (with result) and close agent span via afterInvoke.
        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("acknowledged")));
        rail.afterInvoke(ctx);

        // 5. Close the team span.
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // --- Verify agent span exists ---
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans)
                .as("agent.leader.invoke span missing")
                .hasSize(1);
        SpanData span = agentSpans.get(0);

        // langfuse.observation.type = agent.
        assertThat(attr(span, ObservabilitySemConv.LANGFUSE_OBSERVATION_TYPE))
                .as("observation type should be 'agent'")
                .isEqualTo("agent");

        // Query should be recorded as input.
        assertThat(attr(span, ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT))
                .as("agent input should contain the query")
                .isEqualTo("hello");

        // agentteam.agent.input should also be set.
        assertThat(attr(span, ObservabilitySemConv.AT_AGENT_INPUT))
                .as("agentteam.agent.input should contain the query")
                .isEqualTo("hello");

        // Output should be recorded after afterInvoke.
        assertThat(hasAttr(span, ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT))
                .as("agent output should be present after afterInvoke")
                .isTrue();

        // Team name attribute.
        assertThat(attr(span, ObservabilitySemConv.AT_TEAM_NAME))
                .as("team name attribute should be set")
                .isEqualTo("test_team");

        // Agent name attribute.
        assertThat(attr(span, ObservabilitySemConv.AT_AGENT_NAME))
                .as("agent name attribute should be set")
                .isEqualTo("leader");

        // Session ID attribute.
        assertThat(attr(span, ObservabilitySemConv.AT_SESSION_ID))
                .as("session ID attribute should be set")
                .isEqualTo("test_session");

        // OK status on normal completion.
        assertThat(span.getStatus().getStatusCode())
                .as("agent span should have OK status on normal completion")
                .isEqualTo(StatusCode.OK);
    }

    // ================================================================
    // Rail marks ERROR on exception
    // Mirrors: test_observability_rail_marks_error_on_exception
    // ================================================================

    @Test
    @DisplayName("rail marks ERROR status on exception in afterInvoke")
    void test_observability_rail_marks_error_on_exception() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");

        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Set exception on the context — simulates an error during the agent invoke.
        ctx.setException(new RuntimeException("kaboom"));

        // Restore invoke inputs and close agent span.
        ctx.setInputs(invokeInputsWithResult("hello", null));
        rail.afterInvoke(ctx);

        ObservabilitySetup.finalizeTeamTrace("test_team");

        // --- Verify agent span has ERROR status ---
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans)
                .as("agent span missing after exception")
                .isNotEmpty();
        SpanData span = agentSpans.get(0);

        assertThat(span.getStatus().getStatusCode())
                .as("agent span should have ERROR status after exception")
                .isEqualTo(StatusCode.ERROR);

        // The exception should be recorded as an event on the span.
        assertThat(span.getEvents())
                .as("exception event should be recorded on agent span")
                .isNotEmpty();
    }

    // ================================================================
    // Team span survives after rail iteration ends
    // Mirrors: test_team_span_survives_after_rail_iteration
    // ================================================================

    @Test
    @DisplayName("team span survives after rail iteration ends")
    void test_team_span_survives_after_rail_iteration() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        // Verify team span exists and is valid before the iteration.
        Optional<Span> teamSpanBefore = OtelSpanContext.getTeamSpan();
        assertThat(teamSpanBefore)
                .as("team span should be created")
                .isPresent();
        assertThat(teamSpanBefore.get().getSpanContext().isValid())
                .as("team span should be valid/recording before iteration")
                .isTrue();

        // Rail creates and closes agent span (one iteration).
        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("done")));
        rail.afterInvoke(ctx);

        // KEY VERIFICATION: team span is STILL accessible and valid
        // after the agent span was ended by afterInvoke.
        Optional<Span> teamSpanAfter = OtelSpanContext.getTeamSpan();
        assertThat(teamSpanAfter)
                .as("team span should STILL be accessible after iteration ends")
                .isPresent();
        assertThat(teamSpanAfter.get().getSpanContext().isValid())
                .as("team span should STILL be valid/recording after iteration ends")
                .isTrue();

        // The agent span should be in finished spans (was ended by afterInvoke).
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans)
                .as("agent iteration span should be in exporter (was ended)")
                .isNotEmpty();

        // Verify parent-child relationship: agent span's parent should be team span.
        SpanData agentSpan = agentSpans.get(0);
        assertThat(agentSpan.getParentSpanId())
                .as("agent span's parent should be team span")
                .isEqualTo(teamSpanAfter.get().getSpanContext().getSpanId());

        // Cleanup: finalize team trace.
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // After finalize, team span should be cleared from ThreadLocal.
        assertThat(OtelSpanContext.getTeamSpan())
                .as("team span should be cleared after finalizeTeamTrace")
                .isEmpty();
    }

    // ================================================================
    // Two runs produce two separate traces
    // Mirrors: test_two_runs_produce_two_separate_traces
    // ================================================================

    @Test
    @DisplayName("two runs produce two separate traces with independent trace IDs")
    void test_two_runs_produce_two_separate_traces() {
        StubAgent agent = stubAgent("test_team", "leader");
        ObservabilityRail rail = new ObservabilityRail();

        // --- Run 1 ---
        ObservabilitySetup.startTeamTrace("test_team", "session_1");

        AgentCallbackContext ctx1 = invokeContext(agent, "run 1 query");
        rail.beforeInvoke(ctx1);
        ctx1.setInputs(invokeInputsWithResult("run 1 query", simpleResult("run 1 result")));
        rail.afterInvoke(ctx1);

        // Close team span for run 1 (simulates team_runner finally).
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // Verify team span is cleared after finalize.
        assertThat(OtelSpanContext.getTeamSpan())
                .as("team span should be cleared after finalize (run 1)")
                .isEmpty();

        // --- Run 2 ---
        ObservabilitySetup.startTeamTrace("test_team", "session_2");

        AgentCallbackContext ctx2 = invokeContext(agent, "run 2 query");
        rail.beforeInvoke(ctx2);
        ctx2.setInputs(invokeInputsWithResult("run 2 query", simpleResult("run 2 result")));
        rail.afterInvoke(ctx2);

        ObservabilitySetup.finalizeTeamTrace("test_team");

        // --- Verify: 2 team spans (2 independent traces) ---
        List<SpanData> teamSpans = spansByName("team.test_team");
        assertThat(teamSpans)
                .as("expected 2 team spans (2 traces)")
                .hasSize(2);

        // --- Verify: 2 agent spans (1 per run) ---
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans)
                .as("expected 2 agent spans")
                .hasSize(2);

        // --- Verify: each agent span's parent is a team span ---
        Set<String> teamSpanIds = new HashSet<>();
        for (SpanData ts : teamSpans) {
            teamSpanIds.add(ts.getSpanId());
        }
        for (SpanData as : agentSpans) {
            assertThat(as.getParentSpanId())
                    .as("agent span parent should be a team span")
                    .isIn(teamSpanIds);
        }

        // --- Verify: the two traces have different trace IDs ---
        assertThat(teamSpans.get(0).getTraceId())
                .as("two traces should have different trace IDs")
                .isNotEqualTo(teamSpans.get(1).getTraceId());

        // Each agent span belongs to a different trace.
        assertThat(agentSpans.get(0).getTraceId())
                .as("agent spans should belong to different traces")
                .isNotEqualTo(agentSpans.get(1).getTraceId());
    }

    // ================================================================
    // Full span tree shape verification
    // Mirrors: test_span_tree_shape
    // ================================================================

    @Test
    @DisplayName("span tree shape: team -> agent -> llm/tool, no orphans")
    void test_span_tree_shape() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        // Step 1: Rail creates agent span.
        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "use the calc tool");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Step 2: LLM call within the agent invoke.
        List<Object> messages = List.of(
                Map.of("role", "user", "content", "Use the calc tool."));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Provide response and close LLM span.
        ctx.setInputs(modelCallInputs(messages, simpleResult("42")));
        rail.afterModelCall(ctx);

        // Step 3: Tool call within the agent invoke.
        ToolCallInputs toolInputs = toolCallInputs("calc", "calc-1",
                Map.of("expr", "6*7"), 42);
        ctx.setInputs(toolInputs);
        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);

        // Step 4: Rail closes agent span.
        ctx.setInputs(invokeInputsWithResult("use the calc tool", simpleResult("42")));
        rail.afterInvoke(ctx);

        // Step 5: finalizeTeamTrace closes team span.
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // --- Verify tree shape ---
        List<SpanData> all = finishedSpans();

        // Exactly 1 team span as root.
        List<SpanData> teamSpans = spansByName("team.test_team");
        assertThat(teamSpans)
                .as("expected exactly 1 team span, got %d", teamSpans.size())
                .hasSize(1);
        SpanData teamSpan = teamSpans.get(0);
        // Root span has invalid parent span ID.
        assertThat(teamSpan.getParentSpanId())
                .as("team span should be ROOT (invalid parent)")
                .isEqualTo(SpanId.getInvalid());

        // Exactly 1 agent span, parent = team span.
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans)
                .as("expected exactly 1 agent span, got %d", agentSpans.size())
                .hasSize(1);
        SpanData agentSpan = agentSpans.get(0);
        assertThat(agentSpan.getParentSpanId())
                .as("agent span parent should be team span")
                .isEqualTo(teamSpan.getSpanId());

        // LLM span parent = agent span.
        List<SpanData> llmSpans = spansByName("llm.call");
        assertThat(llmSpans)
                .as("llm.call span should exist")
                .isNotEmpty();
        SpanData llmSpan = llmSpans.get(0);
        assertThat(llmSpan.getParentSpanId())
                .as("llm span parent should be agent span")
                .isEqualTo(agentSpan.getSpanId());

        // Tool span parent = agent span.
        List<SpanData> toolSpans = spansByName("tool.calc");
        assertThat(toolSpans)
                .as("tool.calc span should exist")
                .isNotEmpty();
        SpanData toolSpan = toolSpans.get(0);
        assertThat(toolSpan.getParentSpanId())
                .as("tool span parent should be agent span")
                .isEqualTo(agentSpan.getSpanId());

        // No orphan spans: every non-root span's parent must exist in the set.
        Set<String> spanIds = new HashSet<>();
        for (SpanData s : all) {
            spanIds.add(s.getSpanId());
        }
        for (SpanData s : all) {
            String parentId = s.getParentSpanId();
            if (!SpanId.getInvalid().equals(parentId)) {
                assertThat(spanIds)
                        .as("orphan span: %s parent %s not found in span set",
                                s.getName(), parentId)
                        .contains(parentId);
            }
        }
    }

    // ================================================================
    // Agent span has OK status and no error events on normal completion
    // ================================================================

    @Test
    @DisplayName("agent span has OK status and no error events on normal completion")
    void test_agent_span_ok_status_no_error_events() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "normal run");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ctx.setInputs(invokeInputsWithResult("normal run", simpleResult("ok")));
        rail.afterInvoke(ctx);

        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData agentSpan = spansByPrefix("agent.leader.invoke").get(0);
        assertThat(agentSpan.getStatus().getStatusCode())
                .as("agent span should have OK status on normal completion")
                .isEqualTo(StatusCode.OK);

        // No exception events should be recorded on a successful run.
        boolean hasExceptionEvent = agentSpan.getEvents().stream()
                .anyMatch(e -> "exception".equals(e.getName()));
        assertThat(hasExceptionEvent)
                .as("no exception event should be recorded on normal completion")
                .isFalse();
    }

    // ================================================================
    // Agent span carries conversation ID when provided
    // ================================================================

    @Test
    @DisplayName("agent span carries conversation ID when provided in InvokeInputs")
    void test_agent_span_carries_conversation_id() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        InvokeInputs inputs = InvokeInputs.builder()
                .query("hello")
                .conversationId("conv-123")
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .inputs(inputs)
                .build();

        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("hi")));
        rail.afterInvoke(ctx);

        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData agentSpan = spansByPrefix("agent.leader.invoke").get(0);
        assertThat(attr(agentSpan, ObservabilitySemConv.AT_CONVERSATION_ID))
                .as("agent span should carry the conversation ID")
                .isEqualTo("conv-123");
    }

    // ================================================================
    // Agent span is child of team span (parent-child verification)
    // ================================================================

    @Test
    @DisplayName("agent span parent is team span")
    void test_agent_span_parent_is_team_span() {
        ObservabilitySetup.startTeamTrace("my_team", "test_session");

        StubAgent agent = stubAgent("my_team", "worker");
        AgentCallbackContext ctx = invokeContext(agent, "do work");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ctx.setInputs(invokeInputsWithResult("do work", simpleResult("done")));
        rail.afterInvoke(ctx);

        ObservabilitySetup.finalizeTeamTrace("my_team");

        List<SpanData> teamSpans = spansByName("team.my_team");
        List<SpanData> agentSpans = spansByPrefix("agent.worker.invoke");

        assertThat(teamSpans).as("team span missing").hasSize(1);
        assertThat(agentSpans).as("agent span missing").hasSize(1);

        SpanData teamSpan = teamSpans.get(0);
        SpanData agentSpan = agentSpans.get(0);

        assertThat(agentSpan.getParentSpanId())
                .as("agent span parent should be team span")
                .isEqualTo(teamSpan.getSpanId());
    }

    // ================================================================
    // No agent span created without team span
    // ================================================================

    @Test
    @DisplayName("no agent span created when team span is absent")
    void test_no_agent_span_without_team_span() {
        // Deliberately do NOT call startTeamTrace.
        // OtelSpanContext.getTeamSpan() is empty, so beforeInvoke should skip.
        StubAgent agent = stubAgent("orphan_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "no team");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        ctx.setInputs(invokeInputsWithResult("no team", simpleResult("noop")));
        rail.afterInvoke(ctx);

        // No agent span should have been created.
        List<SpanData> agentSpans = spansByPrefix("agent.leader.invoke");
        assertThat(agentSpans)
                .as("no agent span should be created without a team span")
                .isEmpty();
    }
}
