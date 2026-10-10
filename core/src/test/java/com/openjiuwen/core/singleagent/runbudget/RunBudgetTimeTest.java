/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT-T01~T08: time-dimension budget — per-invoke deadlines, no new dispatch after expiry,
 * timeout clamping (model call / rail retry wait / stream retry wait), the one-shot
 * near-deadline prompt, distinguishable termination and two-dimension coexistence.
 */
class RunBudgetTimeTest {
    private final List<String> toolIds = new ArrayList<>();
    private final List<AgentRail> extraRails = new ArrayList<>();
    private ReActAgent agent;
    private RunBudgetRail rail;

    @AfterEach
    void tearDown() {
        RunBudgetTestFixture.unregisterRail(agent, rail);
        for (AgentRail extraRail : extraRails) {
            if (agent != null) {
                agent.unregisterRail(extraRail).toCompletableFuture().join();
            }
        }
        RunBudgetTestFixture.cleanupTools(toolIds);
    }

    @Test
    void budgetDeclared_effectiveEventPerInvoke() {
        // UT-T01/T02: each invocation computes its own deadline and emits its own event
        // Given a 60s budget and a fast model, invoked twice
        agent = RunBudgetTestFixture.newAgent("time-t01", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(60).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("ok")).model());

        // When invoked twice
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "one"),
                    RunBudgetTestFixture.newSession("t01-a", agent));
            RunBudgetTestFixture.runSync(agent, Map.of("query", "two"),
                    RunBudgetTestFixture.newSession("t01-b", agent));

            // Then each invocation emitted its own effective event
            List<Map<String, Object>> payloads = events.payloadsOf("time_budget_effective");
            assertThat(payloads).hasSize(2);
            assertThat(payloads).allSatisfy(p -> assertThat(p)
                    .containsEntry("total_budget_seconds", 60.0)
                    .containsEntry("source", "configured")
                    .containsEntry("near_deadline_threshold_seconds", 60.0));
            assertThat(events.countOf("turn_budget_guarantee_effective")).isZero();
        }
    }

    @Test
    void expiryBeforeModelCall_noNewDispatch_terminatedIdentifiably() {
        // UT-T03: after expiry no new model call dispatches and the reason is identifiable
        // Given a 1s budget and a 600ms-per-round model that keeps calling tools
        agent = RunBudgetTestFixture.newAgent("time-t03", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(1).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "t03", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("slow", toolId, 999), 600L);
        agent.setLlm(model.model());

        // When invoked
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("t03-s", agent));

        // Then the run terminated at the model-call checkpoint with the time reason
        assertThat(result.get("result_type")).isEqualTo("error");
        assertThat(result.get("output")).isEqualTo("本次执行的时间预算已到期，任务被终止。");
        assertTimeBudgetPayload(result, "model_call");
        assertThat(model.invokeCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void nearDeadline_promptInjectedExactlyOnce() {
        // UT-T06: the wrap-up prompt fires once and only once per invocation
        // Given a 3s budget with a 2s threshold and an 800ms-per-round model
        agent = RunBudgetTestFixture.newAgent("time-t06", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(3).nearDeadlineThresholdSeconds(2).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "t06", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("slow", toolId, 999), 800L);
        agent.setLlm(model.model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("t06-s", agent));

            // Then exactly one near-deadline prompt event fires and the prompt reached the model
            assertThat(events.countOf("time_budget_near_deadline_prompted")).isEqualTo(1);
            assertThat(model.sawSteeringContaining("[时间预算]")).isTrue();
            List<Map<String, Object>> payloads = events.payloadsOf("time_budget_near_deadline_prompted");
            assertThat(payloads.get(0)).containsEntry("threshold_seconds", 2.0);
        }
    }

    @Test
    void modelCallTimeout_clampedToRemainingBudget() {
        // UT-T04: a per-call timeout equal to the remaining budget reaches the model channel
        // Given a 30s budget (below the 60s client default)
        agent = RunBudgetTestFixture.newAgent("time-t04", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(30).build());
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("ok"));
        agent.setLlm(model.model());

        // When invoked
        RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("t04-s", agent));

        // Then the dispatched options carry the clamped timeout
        assertThat(model.lastOptions()).isPresent();
        Float timeout = model.lastOptions().get().getTimeout();
        assertThat(timeout).isNotNull();
        assertThat(timeout.doubleValue()).isBetween(28.0D, 30.0D);
    }

    @Test
    void modelCallTimeout_budgetAboveClientDefault_notClamped() {
        // UT-T04 counterpart: a 120s budget leaves the client default untouched
        // Given a 120s budget
        agent = RunBudgetTestFixture.newAgent("time-t04b", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(120).build());
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("ok"));
        agent.setLlm(model.model());

        // When invoked
        RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("t04b-s", agent));

        // Then no timeout was written into the options
        assertThat(model.lastOptions()).isPresent();
        assertThat(model.lastOptions().get().getTimeout()).isNull();
    }

    @Test
    void railRetryWait_clampedThenTerminatedWithoutNewDispatch() {
        // UT-T05: the rail retry wait is clamped to the remaining budget, never re-granted
        // Given a failing model, a 30s backoff rail and a 1s budget
        agent = RunBudgetTestFixture.newAgent("time-t05", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(1).build());
        registerExtraRail(new LongBackoffRail(50));
        AtomicInteger calls = new AtomicInteger();
        Model failing = mock(Model.class);
        when(failing.supportsKvCacheRelease()).thenReturn(false);
        when(failing.buildKvCacheInvokeKwargs(any(), anyBoolean())).thenReturn(Map.of());
        when(failing.invoke(anyList(), any(ModelInvokeOptions.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            throw new IllegalStateException("model down");
        });
        agent.setLlm(failing);

        // When invoked
        long startNanos = System.nanoTime();
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("t05-s", agent));
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);

        // Then termination came from the budget after ~1s, not after the 30s backoff
        assertTimeBudgetPayload(result, "model_call");
        assertThat(elapsedSeconds).isLessThan(10L);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void modelFailureAtDeadline_noBackoffRail_terminatesWithBudgetSurface() {
        // UT-T05 supplement (E2E regression): a model call dying at the clamped deadline must
        // surface the budget termination, not the transport exception — the rail requests a
        // zero-delay retry so the next beforeModelCall stages the termination
        // Given a model that burns the budget then dies (the clamped-timeout shape), 1s budget
        agent = RunBudgetTestFixture.newAgent("time-t05n", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(1).build());
        AtomicInteger calls = new AtomicInteger();
        Model failing = mock(Model.class);
        when(failing.supportsKvCacheRelease()).thenReturn(false);
        when(failing.buildKvCacheInvokeKwargs(any(), anyBoolean())).thenReturn(Map.of());
        when(failing.invoke(anyList(), any(ModelInvokeOptions.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1200L));
            throw new IllegalStateException("model down");
        });
        agent.setLlm(failing);

        // When invoked
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("t05n-s", agent));

        // Then the run closed with the budget termination surface, not the model error,
        // and the zero-delay retry never re-dispatched the model call
        assertTimeBudgetPayload(result, "model_call");
        assertThat(result.get("output")).isEqualTo("本次执行的时间预算已到期，任务被终止。");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void streamRetryWait_clampedToRemainingBudget() {
        // UT-T05 streaming leg: the internal stream retry sleep is clamped as well
        // Given streaming with an 8s retry delay and a 5s budget
        agent = RunBudgetTestFixture.newAgent("time-t05s", 10);
        RunBudgetTestFixture.configOf(agent).setStreamMaxRetries(1);
        RunBudgetTestFixture.configOf(agent).setStreamRetryDelayMs(8000L);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(5).build());
        Model streaming = mock(Model.class);
        when(streaming.supportsKvCacheRelease()).thenReturn(false);
        when(streaming.buildKvCacheInvokeKwargs(any(), anyBoolean())).thenReturn(Map.of());
        when(streaming.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenAnswer(invocation -> Collections.emptyIterator());
        when(streaming.invoke(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        RunBudgetTestFixture.answer("fallback")));
        agent.setLlm(streaming);

        // When invoked in streaming mode
        long startNanos = System.nanoTime();
        Map<String, Object> result = RunBudgetTestFixture.runStreaming(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("t05s-s", agent));
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);

        // Then the 8s sleep was clamped to the ~5s budget and expiry terminated the run
        assertThat(elapsedSeconds).isLessThan(8L);
        assertTimeBudgetPayload(result, "model_call");
    }

    @Test
    void termination_syncAndStreaming_shareTheSameSurface() {
        // UT-T07: sync and streaming produce the same identifiable termination result
        // Given a 1s budget with a slow tool-looping model, run once per mode
        agent = RunBudgetTestFixture.newAgent("time-t07", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(1).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "t07", toolIds, 0L, toolCalls);

        // When invoked in both modes with equivalent slow models
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("slow", toolId, 501), 600L).model());
        Map<String, Object> syncResult = RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("t07-sync", agent));
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("slow", toolId, 502), 600L).model());
        Map<String, Object> streamResult = RunBudgetTestFixture.runStreaming(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("t07-stream", agent));

        // Then both surfaces carry the same distinguishing reason
        assertTimeBudgetPayload(syncResult, "model_call");
        assertTimeBudgetPayload(streamResult, "model_call");
    }

    @Test
    void twoDimensions_timeExpiry_vetoesTheFinalRound() {
        // UT-T08: time expiry is the hard boundary and vetoes the granted final round
        // Given turn guarantee 10 / hard limit 12, a 1s budget, and a slow tool at round 12
        agent = RunBudgetTestFixture.newAgent("time-t08", 13);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(true).suggestedRounds(10).hardLimit(12).totalBudgetSeconds(2).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String fastTool = RunBudgetTestFixture.registerEchoTool(agent, "t08f", toolIds, 0L, toolCalls);
        String slowTool = RunBudgetTestFixture.registerEchoTool(agent, "t08s", toolIds, 2500L, toolCalls);
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            script.add(RunBudgetTestFixture.toolCall("fast-" + i, fastTool, i + 1));
        }
        script.add(RunBudgetTestFixture.toolCall("valve", slowTool, 12));
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                script, RunBudgetTestFixture.toolCall("never", fastTool, 999));
        agent.setLlm(model.model());

        // When the valve fires at round 12 and the slow tool pushes the run past the deadline
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("t08-s", agent));

            // Then time termination fired at the tool leg and the final-round grant was never emitted
            assertTimeBudgetPayload(result, "tool_dispatch");
            assertThat(events.countOf("turn_budget_final_round_granted")).isZero();
            assertThat(events.countOf("turn_budget_safety_valve_triggered")).isEqualTo(1);
        }
    }

    @Test
    void toolWait_clampedToRemainingBudget() {
        // UT-T04 tool leg: the orTimeout wait is clamped to min(resolved 300s, remaining)
        // Given a 5s budget and a tool that parks 30s (unclamped wait would be 300s)
        agent = RunBudgetTestFixture.newAgent("time-t04t", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(5).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "t04t", toolIds, 30_000L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("slow", toolId, 999)).model());

        // When invoked
        long startNanos = System.nanoTime();
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("t04t-s", agent));
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);

        // Then the tool wait died at the budget boundary instead of parking 30s,
        // and the termination was detected at the tool leg
        assertThat(elapsedSeconds).isLessThan(15L);
        assertThat(toolCalls.get()).isEqualTo(1);
        assertTimeBudgetPayload(result, "tool_dispatch");
    }

    @Test
    void runBudgetContext_nestedAttach_restoresOuterAttachment() {
        // S-1 regression pin: stack pairing of the ThreadLocal bridge
        // Given an outer state attached, When a nested attach/detach runs
        RunBudgetState outer = RunBudgetState.initialize(
                RunBudgetConfig.builder().totalBudgetSeconds(100).build(), bareCtx());
        RunBudgetState inner = RunBudgetState.initialize(
                RunBudgetConfig.builder().totalBudgetSeconds(50).build(), bareCtx());
        RunBudgetContext.attach(outer);
        try {
            Optional<RunBudgetState> displaced = RunBudgetContext.attach(inner);
            RunBudgetContext.detach(displaced);

            // Then the outer attachment is restored
            assertThat(RunBudgetContext.currentRemainingSeconds()).isPresent();
            assertThat(RunBudgetContext.currentRemainingSeconds().getAsDouble())
                    .isBetween(95.0D, 100.0D);
        } finally {
            RunBudgetContext.detach(Optional.empty());
        }
        assertThat(RunBudgetContext.currentRemainingSeconds()).isEmpty();
    }

    private static AgentCallbackContext bareCtx() {
        return new AgentCallbackContext();
    }

    @Test
    void beforeToolCall_dispatchTimeExpiry_terminatesAsToolDispatch() {
        // UT-T03 tool leg: expiry detected at the dispatch gate carries the tool_dispatch marker
        // Given an exhausted time budget state on a bare tool context
        rail = new RunBudgetRail(RunBudgetConfig.builder().turnEnabled(false).totalBudgetSeconds(1).build());
        AgentCallbackContext ctx = bareCtx();
        RunBudgetState state = RunBudgetState.initialize(rail.getConfig(), ctx);
        ctx.getExtra().put(RunBudgetState.STATE_KEY, state);
        ctx.setInputs(new ToolCallInputs());
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1200L));

        // When the dispatch-gate hook runs
        rail.beforeToolCall(ctx);

        // Then termination was staged with the tool_dispatch marker, single-shot
        assertThat(ctx.hasForceFinishRequest()).isTrue();
        Map<String, Object> result = ctx.consumeForceFinish().getResult();
        assertTimeBudgetPayload(result, "tool_dispatch");
        assertThat(state.isTerminated()).isTrue();

        // And a repeated hook invocation stages no new force-finish
        rail.beforeToolCall(ctx);
        assertThat(ctx.hasForceFinishRequest()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static void assertTimeBudgetPayload(Map<String, Object> result, String terminatedAt) {
        assertThat(result.get("result_type")).isEqualTo("error");
        Map<String, Object> timeBudget = (Map<String, Object>) result.get("time_budget");
        assertThat(timeBudget).isNotNull();
        assertThat(timeBudget).containsEntry("reason", "time_budget_exhausted")
                .containsEntry("terminated_at", terminatedAt);
        assertThat(timeBudget).containsKeys("total_budget_seconds", "elapsed_seconds");
        assertThat(result).doesNotContainKey("turn_budget");
    }

    private void registerExtraRail(AgentRail extraRail) {
        extraRails.add(extraRail);
        agent.registerRail(extraRail).toCompletableFuture().join();
    }

    /**
     * Backoff rail double requesting a 30s retry delay on every model failure.
     */
    private static final class LongBackoffRail extends AgentRail {
        private LongBackoffRail(int priority) {
            setPriority(priority);
        }

        /**
         * Requests a long retry delay, which the budget rail must clamp.
         *
         * @param ctx callback context
         * @return completed stage
         */
        @Override
        public void onModelException(AgentCallbackContext ctx) {
            ctx.requestRetry(30.0D);
            return;
        }
    }
}
