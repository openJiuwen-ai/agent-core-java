/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UT-R01~R03 plus the steering-binding supplement: overlay rail termination wins over the
 * budget, prompts of both sources coexist distinguishably, and the steering queue is
 * self-bound when the host did not provide one (and never replaced when it did).
 */
class RunBudgetRailPriorityTest {
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
    void overlayRailForceFinish_budgetDoesNotOverride() {
        // UT-R01: an overlay rail termination inside the guarantee stands untouched
        // Given a priority-80 rail terminating at round 2 and budget guarantee 10
        agent = RunBudgetTestFixture.newAgent("prio-r01", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        Map<String, Object> railResult = new LinkedHashMap<>();
        railResult.put("reason", "rail_decision");
        railResult.put("result_type", "error");
        registerExtraRail(new ForceFinishRail(80, railResult));
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "r01", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("loop", toolId, 999));
        agent.setLlm(model.model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("r01-s", agent));

            // Then the rail result is returned verbatim and the budget stayed silent
            assertThat(result.get("reason")).isEqualTo("rail_decision");
            assertThat(result).doesNotContainKey("turn_budget");
            assertThat(model.sawSteeringContaining("[轮次预算]")).isFalse();
            assertThat(events.countOf("turn_budget_terminated")).isZero();
            assertThat(events.countOf("turn_budget_guarantee_effective")).isEqualTo(1);
        }
    }

    @Test
    void cancellationRailForceFinish_budgetStaysOutOfTheWay() {
        // UT-R03: a user-cancellation-style termination is honored without budget interference
        // Given a priority-100 rail cancelling at the third beforeModelCall
        agent = RunBudgetTestFixture.newAgent("prio-r03", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        registerExtraRail(new CancelBeforeModelCallRail(100, 3));
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "r03", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("loop", toolId, 999));
        agent.setLlm(model.model());

        // When invoked
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("r03-s", agent));

        // Then the cancellation surface returns and the third model call never dispatched
        assertThat(result.get("reason")).isEqualTo("user_cancelled");
        assertThat(result.get("cancelled")).isEqualTo(true);
        assertThat(model.invokeCount()).isEqualTo(2);
        assertThat(result).doesNotContainKey("time_budget");
    }

    @Test
    void budgetPromptAndRailNote_coexistDistinguishably() {
        // UT-R02: budget prompts and rail interventions share one steering message, prefix-split
        // Given a host-stall verdict and a rail note both queued at round 10
        agent = RunBudgetTestFixture.newAgent("prio-r02", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        registerExtraRail(new HostReportRail(50));
        registerExtraRail(new NotePushRail(50, 10, "rail-note-marker"));
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "r02", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                constantToolCalls(toolId), RunBudgetTestFixture.answer("done at 13"));
        agent.setLlm(model.model());

        // When invoked
        RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("r02-s", agent));

        // Then the model sees both sources, each with its own marker
        assertThat(model.sawSteeringContaining("rail-note-marker")).isTrue();
        assertThat(model.sawSteeringContaining("[轮次预算]")).isTrue();
    }

    @Test
    void noQueueBound_railSelfBinds_promptReachesModel() {
        // L2 supplement: without a host-provided steering queue the rail binds its own
        // Given no _steering_queue input and a stall verdict at the first checkpoint
        agent = RunBudgetTestFixture.newAgent("bind-self", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        registerExtraRail(new HostReportRail(50));
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "bind", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                constantToolCalls(toolId), RunBudgetTestFixture.answer("done at 13"));
        agent.setLlm(model.model());

        // When invoked through the plain Runner path (no steering queue in inputs)
        RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("bind-s1", agent));

        // Then the stall prompt still reached the model-visible conversation
        assertThat(model.sawSteeringContaining("[轮次预算]")).isTrue();
    }

    @Test
    void preboundQueue_fromInputs_isUsedNotReplaced() {
        // L2 supplement: a host-bound queue (DeepAgent shape) is never replaced by the rail
        // Given an input-carried queue holding a host marker
        agent = RunBudgetTestFixture.newAgent("bind-host", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        ConcurrentLinkedQueue<String> hostQueue = new ConcurrentLinkedQueue<>();
        hostQueue.offer("host-marker-message");
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done"));
        agent.setLlm(model.model());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "hi");
        inputs.put("_steering_queue", hostQueue);

        // When invoked
        RunBudgetTestFixture.runSync(agent, inputs, RunBudgetTestFixture.newSession("bind-s2", agent));

        // Then the host queue was drained into the conversation (self-bind would have lost it)
        assertThat(model.sawSteeringContaining("host-marker-message")).isTrue();
        assertThat(hostQueue).isEmpty();
    }

    private static List<AssistantMessage> constantToolCalls(String toolId) {
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            script.add(RunBudgetTestFixture.toolCall("same", toolId, i + 1));
        }
        return script;
    }

    private void registerExtraRail(AgentRail extraRail) {
        extraRails.add(extraRail);
        agent.registerRail(extraRail).toCompletableFuture().join();
    }

    /**
     * Overlay rail double that force-finishes with a fixed result at round 2.
     */
    private static final class ForceFinishRail extends AgentRail {
        private final Map<String, Object> result;
        private final AtomicInteger rounds = new AtomicInteger();

        private ForceFinishRail(int priority, Map<String, Object> result) {
            setPriority(priority);
            this.result = result;
        }

        /**
         * Writes the force-finish request when round 2 completes.
         *
         * @param ctx callback context
         */
        @Override
        public void afterModelCall(AgentCallbackContext ctx) {
            if (ctx.getException() == null && rounds.incrementAndGet() == 2) {
                ctx.requestForceFinish(result);
            }
            return;
        }
    }

    /**
     * Cancellation-style rail double writing a user-cancelled force finish in beforeModelCall.
     */
    private static final class CancelBeforeModelCallRail extends AgentRail {
        private final int atCall;
        private final AtomicInteger calls = new AtomicInteger();

        private CancelBeforeModelCallRail(int priority, int atCall) {
            setPriority(priority);
            this.atCall = atCall;
        }

        /**
         * Cancels the run when the configured model call is about to dispatch.
         *
         * @param ctx callback context
         */
        @Override
        public void beforeModelCall(AgentCallbackContext ctx) {
            if (calls.incrementAndGet() == atCall) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("reason", "user_cancelled");
                result.put("cancelled", true);
                ctx.requestForceFinish(result);
            }
            return;
        }
    }

    /**
     * Host-signal rail double reporting no-progress from every afterToolCall hook.
     */
    private static final class HostReportRail extends AgentRail {
        private HostReportRail(int priority) {
            setPriority(priority);
        }

        /**
         * Reports the host no-progress fact to the budget state.
         *
         * @param ctx callback context
         */
        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            RunBudgetSignals.reportNoProgress(ctx, "host saturation evidence", null);
            return;
        }
    }

    /**
     * Note rail double pushing a marker steering message at a fixed model-call round.
     */
    private static final class NotePushRail extends AgentRail {
        private final int atRound;
        private final String note;
        private final AtomicInteger modelCalls = new AtomicInteger();

        private NotePushRail(int priority, int atRound, String note) {
            setPriority(priority);
            this.atRound = atRound;
            this.note = note;
        }

        /**
         * Pushes the note when the configured round completes.
         *
         * @param ctx callback context
         */
        @Override
        public void afterModelCall(AgentCallbackContext ctx) {
            if (ctx.getException() == null && modelCalls.incrementAndGet() == atRound) {
                ctx.pushSteering(note);
            }
            return;
        }
    }
}
