/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-S01~S05: safety-valve sequence (instruction at H, final round at H+1, termination after),
 * natural completion in the final round, extension clamped at the hard limit, and the
 * rail-independent fallback truncation via the raised maxIterations envelope.
 */
class RunBudgetRailSafetyValveTest {
    private final List<String> toolIds = new ArrayList<>();
    private ReActAgent agent;
    private RunBudgetRail rail;

    @AfterEach
    void tearDown() {
        RunBudgetTestFixture.unregisterRail(agent, rail);
        RunBudgetTestFixture.cleanupTools(toolIds);
    }

    @Test
    void atHardLimit_instructionThenFinalRoundThenTermination() {
        // UT-S01/S02: the valve sequence is instruction, one final round, then termination
        // Given guarantee 10, hard limit 12, envelope 13 and a model that never finishes
        agent = RunBudgetTestFixture.newAgent("valve-s01", 13);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder().turnEnabled(true)
                .suggestedRounds(10).hardLimit(12).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "s01", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("loop", toolId, 999));
        agent.setLlm(model.model());

        // When the loop reaches the hard limit
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("s01-s", agent));

            // Then the termination surface and the event sequence match the contract
            assertThat(result.get("result_type")).isEqualTo("error");
            assertThat(result.get("output")).isEqualTo("已达到轮次预算绝对上限，任务被终止。");
            @SuppressWarnings("unchecked")
            Map<String, Object> turnBudget = (Map<String, Object>) result.get("turn_budget");
            assertThat(turnBudget).containsEntry("reason", "turn_budget_exhausted")
                    .containsEntry("round", 13)
                    .containsEntry("hard_limit", 12)
                    .containsEntry("guaranteed_rounds", 10);
            List<String> valveSequence = List.of("turn_budget_safety_valve_triggered",
                    "turn_budget_final_round_granted", "turn_budget_terminated");
            int lastIndex = -1;
            for (String expected : valveSequence) {
                int index = events.types().indexOf(expected);
                assertThat(index).isGreaterThan(lastIndex);
                lastIndex = index;
            }
            assertThat(events.payloadsOf("turn_budget_safety_valve_triggered").get(0))
                    .containsEntry("round", 12)
                    .containsEntry("final_round", 13)
                    .containsEntry("instruction_injected", true);
            assertThat(events.payloadsOf("turn_budget_final_round_granted")).hasSize(1);
            assertThat(model.sawSteeringContaining("必须立即收尾")).isTrue();
        }
    }

    @Test
    void finalRound_naturalAnswer_noTermination() {
        // UT-S03: a natural answer in the final round is not terminated
        // Given the same valve setup but the model answers in the final round
        agent = RunBudgetTestFixture.newAgent("valve-s03", 13);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder().turnEnabled(true)
                .suggestedRounds(10).hardLimit(12).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "s03", toolIds, 0L, toolCalls);
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            script.add(RunBudgetTestFixture.toolCall("loop-" + i, toolId, i + 1));
        }
        script.add(RunBudgetTestFixture.answer("concluded in final round"));
        agent.setLlm(RunBudgetTestFixture.scriptedModel(script, RunBudgetTestFixture.answer("x")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("s03-s", agent));

            // Then the answer returns and no termination event fires
            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("concluded in final round");
            assertThat(events.countOf("turn_budget_safety_valve_triggered")).isEqualTo(1);
            assertThat(events.countOf("turn_budget_terminated")).isZero();
        }
    }

    @Test
    void checkpointExtension_neverExceedsHardLimit() {
        // UT-S04: extension is clamped so the valve always takes over at the hard limit
        // Given guarantee 10, hard limit 20 and continuous progress
        agent = RunBudgetTestFixture.newAgent("valve-s04", 21);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder().turnEnabled(true)
                .suggestedRounds(10).hardLimit(20).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "s04", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("loop", toolId, 999)).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("s04-s", agent));

            // Then the valve terminates at round 21 despite progress extensions
            assertThat(result.get("result_type")).isEqualTo("error");
            @SuppressWarnings("unchecked")
            Map<String, Object> turnBudget = (Map<String, Object>) result.get("turn_budget");
            assertThat(turnBudget).containsEntry("round", 21).containsEntry("hard_limit", 20);
            assertThat(events.payloadsOf("turn_budget_extended"))
                    .allSatisfy(p -> assertThat((Integer) p.get("allowed_rounds")).isLessThanOrEqualTo(21));
        }
    }

    @Test
    void inertRail_fallbackEnvelope_truncatesWithoutValve() {
        // UT-S05: with the rail inert, the raised maxIterations envelope still truncates
        // Given a budget rail that never acts and maxIterations = hardLimitMax + 1
        agent = RunBudgetTestFixture.newAgent("valve-s05", 13);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder().turnEnabled(true)
                .suggestedRounds(10).hardLimit(12).build());
        InertBudgetRail inertRail = new InertBudgetRail(RunBudgetConfig.builder().turnEnabled(true)
                .suggestedRounds(10).hardLimit(12).build());
        agent.unregisterRail(rail).toCompletableFuture().join();
        rail = null;
        agent.registerRail(inertRail).toCompletableFuture().join();
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "s05", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("loop", toolId, 999)).model());

        // When the loop exhausts the envelope
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("s05-s", agent));

        // Then the legacy truncation surface appears at the envelope boundary
        assertThat(result.get("result_type")).isEqualTo("error");
        assertThat(result.get("output")).isEqualTo("Max iterations reached without completion");
        assertThat(result).doesNotContainKey("turn_budget");
        assertThat(toolCalls.get()).isEqualTo(13);

        agent.unregisterRail(inertRail).toCompletableFuture().join();
    }

    @Test
    void answerWithPendingSteering_atHardLimit_fullValveSequencePreserved() {
        // Edge: at H the model answers but pending steering defers termination; the valve must
        // still trigger at H so the four-point event sequence stays intact when H+1 keeps working
        // Given guarantee 10, hard limit 12, and a steering push at round 12 (which answers)
        agent = RunBudgetTestFixture.newAgent("valve-edge", 13);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(true).suggestedRounds(10).hardLimit(12).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "sedge", toolIds, 0L, toolCalls);
        agent.registerRail(new LateSteeringRail(50, 12)).toCompletableFuture().join();
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            script.add(RunBudgetTestFixture.toolCall("loop-" + i, toolId, i + 1));
        }
        script.add(RunBudgetTestFixture.answer("deferred answer at 12"));
        agent.setLlm(RunBudgetTestFixture.scriptedModel(script,
                RunBudgetTestFixture.toolCall("still working", toolId, 999)).model());

        // When round 12 defers on pending steering and round 13 keeps calling tools
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("sedge-s", agent));

            // Then the full sequence fired: trigger (H) -> final round granted (H+1) -> terminated
            assertThat(result.get("result_type")).isEqualTo("error");
            assertThat(result).containsKey("turn_budget");
            assertThat(events.countOf("turn_budget_safety_valve_triggered")).isEqualTo(1);
            assertThat(events.countOf("turn_budget_final_round_granted")).isEqualTo(1);
            assertThat(events.countOf("turn_budget_terminated")).isEqualTo(1);
        }
    }

    /**
     * Rail double pushing steering exactly when the given model-call round completes.
     */
    private static final class LateSteeringRail extends com.openjiuwen.core.singleagent.rail.AgentRail {
        private final int atRound;
        private final AtomicInteger modelCalls = new AtomicInteger();

        private LateSteeringRail(int priority, int atRound) {
            setPriority(priority);
            this.atRound = atRound;
        }

        /**
         * Pushes steering when the configured round completes.
         *
         * @param ctx callback context
         * @return completed stage
         */
        @Override
        public void afterModelCall(AgentCallbackContext ctx) {
            if (ctx.getException() == null && modelCalls.incrementAndGet() == atRound) {
                ctx.pushSteering("late steering note");
            }
            return;
        }
    }

    /**
     * Rail double that initializes state but never acts, simulating a failed budget rail.
     */
    private static final class InertBudgetRail extends RunBudgetRail {
        private InertBudgetRail(RunBudgetConfig config) {
            super(config);
        }

        /**
         * Suppresses all post-model-call budget actions.
         *
         * @param ctx callback context
         * @return completed stage
         */
        @Override
        public void afterModelCall(AgentCallbackContext ctx) {
            return;
        }
    }
}
