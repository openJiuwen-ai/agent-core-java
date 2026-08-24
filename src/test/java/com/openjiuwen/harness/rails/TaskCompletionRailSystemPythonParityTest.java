/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.StopEvaluationContext;
import com.openjiuwen.harness.task_loop.LoopCoordinator;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestTaskCompletionRailSystem} in
 * {@code tests/system_tests/harness/rail/test_task_completion_rail.py}.
 */
class TaskCompletionRailSystemPythonParityTest {

    @Test
    void testUc1MaxRoundsStopsLoop() {
        TaskCompletionRail rail = new TaskCompletionRail(null, null, 1, false, 3, null, List.of());

        int completedRounds = runUntilStop(rail.buildEvaluators(), 10, 0.0D);

        assertThat(completedRounds).isEqualTo(3);
    }

    @Test
    void testUc2CompletionPromiseMockLlm() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "TASK_DONE", 1, false, 5, null, List.of());
        List<StopConditionEvaluator> evaluators = rail.buildEvaluators();
        CompletionPromiseEvaluator promiseEvaluator = evaluators.stream()
                .filter(CompletionPromiseEvaluator.class::isInstance)
                .map(CompletionPromiseEvaluator.class::cast)
                .findFirst()
                .orElseThrow();
        CallbackContext context = contextWithAgent(new CoordinatorAgent(evaluators),
                "result", Map.of("output", "分析完成。<promise>TASK_DONE</promise>"));

        rail.afterTaskIteration(context);

        assertThat(promiseEvaluator.shouldStop(stopContext(1, 0.0D))).isTrue();
    }

    @Test
    void testUc3TaskInstructionWrapsFirstQuery() {
        TaskCompletionRail rail = new TaskCompletionRail("请完成以下任务：{query}", null, 1, false, 2, null, List.of());
        CallbackContext context = contextWithAgent(new DeepAgent(new AgentCard("deep", "deep", "test")),
                "query", "step-1",
                "is_follow_up", false);

        rail.beforeTaskIteration(context);

        assertThat(context.get("query")).asString()
                .contains("请完成以下任务：")
                .contains("step-1");
    }

    @Test
    void testUc4CustomPredicateStopsLoop() {
        StopConditionEvaluator stopAfterTwo = ctx -> ctx.getIteration() >= 2;
        TaskCompletionRail rail = new TaskCompletionRail(null, null, 1, false, null, null, List.of(stopAfterTwo));

        int completedRounds = runUntilStop(rail.buildEvaluators(), 5, 0.0D);

        assertThat(completedRounds).isEqualTo(2);
    }

    @Test
    void testUc5TimeoutStopsLoop() {
        TaskCompletionRail rail = new TaskCompletionRail(null, null, 1, false, null, 0.6D, List.of());
        List<StopConditionEvaluator> evaluators = rail.buildEvaluators();

        int completedRounds = runUntilStop(evaluators, 10, 0.4D);

        assertThat(completedRounds).isBetween(1, 3);
        assertThat(completedRounds).isEqualTo(2);
    }

    private static int runUntilStop(List<StopConditionEvaluator> evaluators, int plannedTasks, double secondsPerRound) {
        for (int iteration = 1; iteration <= plannedTasks; iteration++) {
            StopEvaluationContext context = stopContext(iteration, iteration * secondsPerRound);
            if (evaluators.stream().anyMatch(evaluator -> evaluator.shouldStop(context))) {
                return iteration;
            }
        }
        return plannedTasks;
    }

    private static StopEvaluationContext stopContext(int iteration, double elapsedSeconds) {
        return new StopEvaluationContext(iteration, 0, elapsedSeconds, null, Map.of());
    }

    private static CallbackContext contextWithAgent(DeepAgent agent, Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return new CallbackContext(agent, map);
    }

    private static final class CoordinatorAgent extends DeepAgent {

        private final LoopCoordinator coordinator;

        private CoordinatorAgent(List<StopConditionEvaluator> evaluators) {
            super(new AgentCard("deep", "deep", "test"));
            this.coordinator = new LoopCoordinator(evaluators);
        }

        @Override
        public LoopCoordinator loopCoordinator() {
            return coordinator;
        }
    }
}
