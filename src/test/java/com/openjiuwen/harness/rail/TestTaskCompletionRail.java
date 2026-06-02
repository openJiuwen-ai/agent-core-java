/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.CustomPredicateEvaluator;
import com.openjiuwen.harness.schema.MaxRoundsEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.StopEvaluationContext;
import com.openjiuwen.harness.schema.TimeoutEvaluator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for TaskCompletionRail.
 * <p>
 * Mirrors Python's {@code test_task_completion_rail.py} in
 * {@code tests.system_tests.harness.rail}.
 */
@Tag("system-test")
class TestTaskCompletionRail {

    @Test
    void testUc1MaxRoundsStopsLoop() {
        TaskCompletionRail rail = new TaskCompletionRail(null, null, 1,
                false, 3, null);
        MaxRoundsEvaluator evaluator = (MaxRoundsEvaluator) rail.buildEvaluators().get(0);

        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().iteration(2).build())).isFalse();
        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().iteration(3).build())).isTrue();
    }

    @Test
    void testUc3TaskInstructionWrapsFirstQuery() {
        TaskCompletionRail rail = new TaskCompletionRail("请完成以下任务：{query}",
                null, 1, false, 2, null);
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setQuery("step-1");

        rail.beforeTaskIteration(AgentCallbackContext.builder().inputs(inputs).build());

        assertThat(inputs.getQuery()).contains("请完成以下任务：").contains("step-1");
    }

    @Test
    void testUc4CustomPredicateStopsLoop() {
        TaskCompletionRail rail = new TaskCompletionRail(null, null, 1,
                false, null, null,
                List.of(new CustomPredicateEvaluator(ctx -> ctx.getIteration() >= 2)));
        StopConditionEvaluator evaluator = rail.buildEvaluators().get(0);

        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().iteration(1).build())).isFalse();
        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().iteration(2).build())).isTrue();
    }

    @Test
    void testUc5TimeoutStopsLoop() {
        TaskCompletionRail rail = new TaskCompletionRail(null, null, 1,
                false, null, 0.6);
        TimeoutEvaluator evaluator = (TimeoutEvaluator) rail.buildEvaluators().get(0);

        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().elapsedSeconds(0.4).build())).isFalse();
        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().elapsedSeconds(0.6).build())).isTrue();
    }

    @Test
    void testUc2CompletionPromiseMockLlm() {
        String promise = "TASK_DONE";
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator(promise);
        TaskCompletionRail rail = new TaskCompletionRail(null, promise, 1,
                false, 5, null);
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setResult(Map.of("output", "分析完成。<promise>TASK_DONE</promise>"));

        rail.afterTaskIteration(AgentCallbackContext.builder()
                .agent(new FakeAgent(evaluator))
                .inputs(inputs)
                .build());

        assertThat(evaluator.shouldStop(StopEvaluationContext.builder().build())).isTrue();
    }

    private static final class FakeAgent {
        private final FakeCoordinator loopCoordinator;

        private FakeAgent(CompletionPromiseEvaluator evaluator) {
            this.loopCoordinator = new FakeCoordinator(evaluator);
        }
    }

    private static final class FakeCoordinator {
        private final CompletionPromiseEvaluator evaluator;

        private FakeCoordinator(CompletionPromiseEvaluator evaluator) {
            this.evaluator = evaluator;
        }

        public CompletionPromiseEvaluator getCompletionPromiseEvaluator() {
            return evaluator;
        }
    }
}
