/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * L2 supplement: interrupt/resume opens a new budget cycle; two sequential invocations of the
 * same agent keep fully independent budget state (rounds restart, deadlines recompute, and the
 * state never lands on the shared agent instance). The literal interrupt-resume envelope caveat
 * (L2 4.2: startIteration accumulates across resumes) is exercised by the container E2E; this
 * class pins the per-invoke isolation that the cycle-reset semantics rest on.
 */
class RunBudgetRailResumeTest {
    private final List<String> toolIds = new ArrayList<>();
    private ReActAgent agent;
    private RunBudgetRail rail;

    @AfterEach
    void tearDown() {
        RunBudgetTestFixture.unregisterRail(agent, rail);
        RunBudgetTestFixture.cleanupTools(toolIds);
    }

    @Test
    void secondInvoke_startsAFreshBudgetCycle() {
        // Given an agent that ran one 12-round progressing invocation already
        agent = RunBudgetTestFixture.newAgent("resume-cycle", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "resume", toolIds, 0L, toolCalls);

        // When a second invocation runs 5 rounds afterwards
        List<AssistantMessage> script = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            script.add(RunBudgetTestFixture.toolCall("round-" + i, toolId, i + 1));
        }
        script.add(RunBudgetTestFixture.answer("first done"));
        for (int i = 0; i < 4; i++) {
            script.add(RunBudgetTestFixture.toolCall("again-" + i, toolId, 100 + i));
        }
        agent.setLlm(RunBudgetTestFixture.scriptedModel(script,
                RunBudgetTestFixture.answer("second done")).model());

        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            Map<String, Object> first = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi"), RunBudgetTestFixture.newSession("resume-s1", agent));
            Map<String, Object> second = RunBudgetTestFixture.runSync(
                    agent, Map.of("query", "hi again"), RunBudgetTestFixture.newSession("resume-s2", agent));

            // Then both invocations completed, each emitted its own guarantee event, and the
            // second (5 rounds) saw no checkpoint evaluation despite the first reaching round 12
            assertThat(first.get("result_type")).isEqualTo("answer");
            assertThat(second.get("result_type")).isEqualTo("answer");
            assertThat(second.get("output")).isEqualTo("second done");
            assertThat(events.countOf("turn_budget_guarantee_effective")).isEqualTo(2);
            assertThat(events.countOf("turn_budget_checkpoint")).isEqualTo(1);
        }
    }
}
