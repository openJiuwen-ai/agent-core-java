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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UT-B01~B09: turn-dimension guarantee semantics, checkpoint evaluation and extension,
 * stall prompts (host-signal driven), prompt escalation and gentle reminders.
 */
class RunBudgetRailTurnBudgetTest {
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
    void withinGuarantee_completion_zeroIntervention() {
        // UT-B01: a task finishing inside the guarantee sees no budget intervention
        // Given guarantee 10 and a model answering in round 1
        agent = RunBudgetTestFixture.newAgent("turn-b01", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("b01-s", agent));

            // Then the answer is normal and only the guarantee-effective event fired
            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("done");
            assertThat(events.countOf("turn_budget_guarantee_effective")).isEqualTo(1);
            assertThat(events.types()).noneMatch(type -> type.startsWith("turn_budget_")
                    && !"turn_budget_guarantee_effective".equals(type));
        }
    }

    @Test
    void guaranteeEvent_configuredSuggestion_reportsValueAndSource() {
        // UT-B02/C02: guarantee-effective event carries value, source and hard limit
        // Given suggested rounds 12
        agent = RunBudgetTestFixture.newAgent("turn-b02", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(12).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("b02-s", agent));

            // Then the event reports the configured guarantee
            List<Map<String, Object>> payloads = events.payloadsOf("turn_budget_guarantee_effective");
            assertThat(payloads).hasSize(1);
            assertThat(payloads.get(0)).containsEntry("guaranteed_rounds", 12)
                    .containsEntry("source", "configured")
                    .containsEntry("hard_limit", 200);
        }
    }

    @Test
    void guaranteeEvent_noSuggestion_reportsDefaultThirty() {
        // UT-C03: undeclared suggestion falls back to the default guarantee 30
        // Given no suggestion declared
        agent = RunBudgetTestFixture.newAgent("turn-c03", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder().turnEnabled(true).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("c03-s", agent));

            // Then the event reports the default guarantee
            List<Map<String, Object>> payloads = events.payloadsOf("turn_budget_guarantee_effective");
            assertThat(payloads).hasSize(1);
            assertThat(payloads.get(0)).containsEntry("guaranteed_rounds", 30)
                    .containsEntry("source", "default")
                    .containsEntry("hard_limit", 200);
        }
    }

    @Test
    void beyondGuarantee_withContinuousProgress_completesNaturally() {
        // UT-B03: the guarantee is a floor, not a ceiling
        // Given guarantee 10 and a task progressing until round 12
        agent = RunBudgetTestFixture.newAgent("turn-b03", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b03", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                progressiveToolCalls(toolId, 11), RunBudgetTestFixture.answer("finished at 12")).model());

        // When invoked
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("b03-s", agent));

        // Then the task completes past the guarantee without termination
        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(result.get("output")).isEqualTo("finished at 12");
        assertThat(result).doesNotContainKey("turn_budget");
    }

    @Test
    void progressAtCheckpoints_intervalGrowsByStep() {
        // UT-B04: sustained progress lengthens the interval by +10 each time
        // Given guarantee 10 (checkpoints at 10, 20, 40)
        agent = RunBudgetTestFixture.newAgent("turn-b04", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b04", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                progressiveToolCalls(toolId, 50), RunBudgetTestFixture.answer("done at 51")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("b04-s", agent));

            // Then three extensions fire with growing intervals
            assertThat(result.get("result_type")).isEqualTo("answer");
            List<Map<String, Object>> extended = events.payloadsOf("turn_budget_extended");
            assertThat(extended).hasSize(3);
            assertThat(extended.stream().map(p -> p.get("next_interval")).toList())
                    .containsExactly(10, 20, 30);
            assertThat(extended.stream().map(p -> p.get("allowed_rounds")).toList())
                    .containsExactly(20, 40, 70);
            assertThat(events.payloadsOf("turn_budget_checkpoint"))
                    .allSatisfy(p -> assertThat(p).containsEntry("progressed", true));
        }
    }

    @Test
    void progressAtCheckpoints_intervalCappedAtMaxInterval() {
        // UT-B04: the interval never exceeds the configured cap
        // Given a custom cap of 20
        agent = RunBudgetTestFixture.newAgent("turn-b04c", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder().turnEnabled(true)
                .suggestedRounds(10).maxCheckpointInterval(20).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b04c", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                progressiveToolCalls(toolId, 65), RunBudgetTestFixture.answer("done at 66")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("b04c-s", agent));

            // Then intervals saturate at the cap
            List<Map<String, Object>> extended = events.payloadsOf("turn_budget_extended");
            assertThat(extended.stream().map(p -> p.get("next_interval")).toList())
                    .containsExactly(10, 20, 20, 20);
        }
    }

    @Test
    void checkpointEvaluation_makesNoExtraModelCalls() {
        // UT-B05: progress evaluation is local heuristics only, zero model calls
        // Given a progressing task over 15 rounds
        agent = RunBudgetTestFixture.newAgent("turn-b05", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b05", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                progressiveToolCalls(toolId, 14), RunBudgetTestFixture.answer("done at 15"));
        agent.setLlm(model.model());

        // When invoked
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("b05-s", agent));

        // Then exactly one model call per round happened
        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(model.invokeCount()).isEqualTo(15);
    }

    @Test
    void hostStallReport_overridesSurfaceProgress_promptInjected() {
        // UT-B06: host-reported no-progress drives the stall verdict even with new tool results
        // Given a host rail reporting stall each round and a tool-looping model
        agent = RunBudgetTestFixture.newAgent("turn-b06", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b06", toolIds, 0L, toolCalls);
        registerExtraRail(new HostStallRail(50, null));
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                constantToolCalls(toolId, 12), RunBudgetTestFixture.answer("done at 13"));
        agent.setLlm(model.model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("b06-s", agent));

            // Then the checkpoint at 10 judges stall and injects a prompt visible to the model
            List<Map<String, Object>> checkpoints = events.payloadsOf("turn_budget_checkpoint");
            assertThat(checkpoints).hasSize(1);
            assertThat(checkpoints.get(0)).containsEntry("progressed", false)
                    .containsEntry("host_stall_reported", true);
            List<Map<String, Object>> stalls = events.payloadsOf("turn_budget_stall_prompted");
            assertThat(stalls).hasSize(1);
            assertThat(stalls.get(0)).containsEntry("source", "host")
                    .containsEntry("escalated", false);
            assertThat(model.sawSteeringContaining("[轮次预算]")).isTrue();
        }
    }

    @Test
    void hostStallReport_withWording_usesHostPromptText() {
        // UT-B06: host-provided wording wins over the core default prompt
        // Given a host rail supplying custom wording
        agent = RunBudgetTestFixture.newAgent("turn-b06h", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b06h", toolIds, 0L, toolCalls);
        registerExtraRail(new HostStallRail(50, "HOST-WORDING: 检索结果已饱和，请直接总结。"));
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                constantToolCalls(toolId, 12), RunBudgetTestFixture.answer("done at 13"));
        agent.setLlm(model.model());

        // When invoked
        RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                RunBudgetTestFixture.newSession("b06h-s", agent));

        // Then the model sees the host wording
        assertThat(model.sawSteeringContaining("HOST-WORDING")).isTrue();
    }

    @Test
    void pendingSteering_atAnswerRound_defersTerminationOneRound() {
        // UT-B07: hasPendingSteering lets the model respond to an injected prompt
        // Given a rail pushing steering while the model answers in round 2
        agent = RunBudgetTestFixture.newAgent("turn-b07", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b07", toolIds, 0L, toolCalls);
        registerExtraRail(new SteeringPushRail(50, 2, "host-note"));
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(RunBudgetTestFixture.toolCall("c1", toolId, 1),
                        RunBudgetTestFixture.answer("intermediate answer")),
                RunBudgetTestFixture.answer("final after steering")).model());

        // When invoked
        Map<String, Object> result = RunBudgetTestFixture.runSync(
                agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("b07-s", agent));

        // Then the loop continued one extra round and returned the later answer
        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(result.get("output")).isEqualTo("final after steering");
    }

    @Test
    void consecutiveStalls_reachingThreshold_escalatesPromptStrength() {
        // UT-B08: three consecutive stalled checkpoints escalate the prompt
        // Given constant stall reports through three checkpoints (10, 20, 30)
        agent = RunBudgetTestFixture.newAgent("turn-b08", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b08", toolIds, 0L, toolCalls);
        registerExtraRail(new HostStallRail(50, null));
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                constantToolCalls(toolId, 33), RunBudgetTestFixture.answer("done at 34"));
        agent.setLlm(model.model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("b08-s", agent));

            // Then three stall prompts fire and the third is escalated
            List<Map<String, Object>> stalls = events.payloadsOf("turn_budget_stall_prompted");
            assertThat(stalls).hasSize(3);
            assertThat(stalls.get(0)).containsEntry("escalated", false);
            assertThat(stalls.get(2)).containsEntry("escalated", true)
                    .containsEntry("consecutive_stalls", 3);
            assertThat(model.sawSteeringContaining("不要再重复相同的尝试")).isTrue();
        }
    }

    @Test
    void gentleReminder_firesOnCadence_evenWithProgress() {
        // UT-B09: gentle reminders are driven by rounds alone, independent of stall verdicts
        // Given guarantee 10 (reminder zone starts past round 20) and continuous progress
        agent = RunBudgetTestFixture.newAgent("turn-b09", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "b09", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                progressiveToolCalls(toolId, 45), RunBudgetTestFixture.answer("done at 46")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("b09-s", agent));

            // Then two reminders fire (rounds 21 and 36) and no stall prompt exists
            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(events.payloadsOf("turn_budget_gentle_reminder")).hasSize(2);
            assertThat(events.countOf("turn_budget_stall_prompted")).isZero();
        }
    }

    private static List<AssistantMessage> progressiveToolCalls(String toolId, int count) {
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            script.add(RunBudgetTestFixture.toolCall("progress-" + i, toolId, i + 1));
        }
        return script;
    }

    private static List<AssistantMessage> constantToolCalls(String toolId, int count) {
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            script.add(RunBudgetTestFixture.toolCall("same", toolId, i + 1));
        }
        return script;
    }

    private void registerExtraRail(AgentRail extraRail) {
        extraRails.add(extraRail);
        agent.registerRail(extraRail).toCompletableFuture().join();
    }

    /**
     * Host-signal rail double reporting no-progress from every afterToolCall hook.
     */
    private static final class HostStallRail extends AgentRail {
        private final String promptText;

        private HostStallRail(int priority, String promptText) {
            setPriority(priority);
            this.promptText = promptText;
        }

        /**
         * Reports the host no-progress fact to the budget state.
         *
         * @param ctx callback context
         */
        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            RunBudgetSignals.reportNoProgress(ctx, "search saturation detected by host", promptText);
            return;
        }
    }

    /**
     * Steering rail double pushing one steering message at a fixed model-call round.
     */
    private static final class SteeringPushRail extends AgentRail {
        private final int atRound;
        private final String note;
        private final AtomicInteger modelCalls = new AtomicInteger();

        private SteeringPushRail(int priority, int atRound, String note) {
            setPriority(priority);
            this.atRound = atRound;
            this.note = note;
        }

        /**
         * Pushes the steering note when the configured round answers.
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
