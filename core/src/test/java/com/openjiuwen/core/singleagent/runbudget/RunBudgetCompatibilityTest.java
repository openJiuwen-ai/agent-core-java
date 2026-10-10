/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.agents.ReActAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UT-C01: with no budget config declared (rail not registered), the main loop behavior is
 * byte-identical to the pre-feature baseline (result surface, truncation text, no budget events).
 */
class RunBudgetCompatibilityTest {
    private final List<String> toolIds = new ArrayList<>();
    private ReActAgent agent;
    private RunBudgetRail rail;

    @AfterEach
    void tearDown() {
        RunBudgetTestFixture.unregisterRail(agent, rail);
        RunBudgetTestFixture.cleanupTools(toolIds);
    }

    @Test
    void legacyTruncation_noRail_unchangedResultSurface() {
        // Given a legacy agent with maxIterations=3 and no budget rail
        agent = RunBudgetTestFixture.newAgent("compat-truncate", 3);
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "compat", toolIds, 0L, toolCalls);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(RunBudgetTestFixture.toolCall("c1", toolId, 1)),
                RunBudgetTestFixture.toolCall("loop", toolId, 99));
        agent.setLlm(model.model());

        // When the loop exhausts the hard limit
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("compat-s1", agent));

            // Then the legacy truncation surface is byte-identical
            assertThat(result.get("result_type")).isEqualTo("error");
            assertThat(result.get("output")).isEqualTo("Max iterations reached without completion");
            assertThat(result).doesNotContainKeys("turn_budget", "time_budget");
            assertThat(events.types()).noneMatch(type -> type.startsWith("turn_budget_"));
            assertThat(events.types()).noneMatch(type -> type.startsWith("time_budget_"));
        }
    }

    @Test
    void normalAnswer_noRail_unchangedResultKeys() {
        // Given a legacy agent answering in round 1
        agent = RunBudgetTestFixture.newAgent("compat-answer", 5);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());

        // When invoked without any budget rail
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("compat-s2", agent));

            // Then the answer surface contains exactly the legacy keys
            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("done");
            assertThat(result).doesNotContainKeys("turn_budget", "time_budget");
            assertThat(events.types()).noneMatch(type -> type.startsWith("turn_budget_"));
        }
    }

    @Test
    void turnOnlyRail_noTimeBudget_zeroTimeDimensionEffects() {
        // Given only the turn dimension enabled
        agent = RunBudgetTestFixture.newAgent("compat-turn-only", 5);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> result = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("compat-s3", agent));

            // Then no time-dimension event or timeout clamp appears
            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(events.types()).noneMatch(type -> type.startsWith("time_budget_"));
        }
    }
}
