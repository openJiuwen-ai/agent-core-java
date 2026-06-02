/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.StopEvaluationContext;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for task completion loop extension points.
 * <p>
 * Mirrors Python's {@code test_task_completion_extensions} in
 * {@code tests.unit_tests.harness.test_task_completion_extensions}.
 */
@Tag("unit-test")
class TaskCompletionExtensionsTest {

    @Test
    @DisplayName("A promise block may start with the token and include details")
    void testPromiseBlockCanIncludeEvidenceLines() {
        String text = """
                done
                <promise>all_tasks_completed
                Completed tasks:
                - created output
                </promise>
                """;

        String block = TaskCompletionRail.extractPromiseBlock(text);

        assertNotNull(block);
        assertTrue(TaskCompletionRail.promiseMatches(block, "all_tasks_completed"));
        assertFalse(TaskCompletionRail.promiseMatches(block, "different_token"));
    }

    @Test
    @DisplayName("Default behavior remains exact promise token matching")
    void testTaskCompletionRailRejectsDetailsByDefault() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed");
        TaskCompletionRail rail = new TaskCompletionRail(null, "all_tasks_completed",
                1, false, null, null);

        rail.afterTaskIteration(ctxWithOutput(evaluator, """
                <promise>all_tasks_completed
                Completed tasks:
                - created output
                </promise>"""));

        assertFalse(evaluator.shouldStop(StopEvaluationContext.builder().build()));
    }

    @Test
    @DisplayName("Detailed promise blocks require an explicit opt-in")
    void testTaskCompletionRailAcceptsDetailsWhenEnabled() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed");
        TaskCompletionRail rail = new TaskCompletionRail(null, "all_tasks_completed",
                1, true, null, null);

        rail.afterTaskIteration(ctxWithOutput(evaluator, """
                <promise>all_tasks_completed
                Completed tasks:
                - created output
                </promise>"""));

        assertTrue(evaluator.shouldStop(StopEvaluationContext.builder().build()));
    }

    @Test
    @DisplayName("TaskCompletionRail forwards required confirmation count")
    void testTaskCompletionRailBuildsMultiConfirmationEvaluator() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "all_tasks_completed",
                2, false, null, null);
        List<StopConditionEvaluator> evaluators = rail.buildEvaluators();
        CompletionPromiseEvaluator evaluator = assertInstanceOf(CompletionPromiseEvaluator.class, evaluators.get(0));

        evaluator.notifyFulfilled("all_tasks_completed");
        assertFalse(evaluator.shouldStop(StopEvaluationContext.builder().build()));
        evaluator.notifyFulfilled("all_tasks_completed");
        assertTrue(evaluator.shouldStop(StopEvaluationContext.builder().build()));
    }

    @Test
    @DisplayName("notify_absent resets the consecutive confirmation streak")
    void testCompletionPromiseEvaluatorRequiresConsecutiveConfirmations() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed", 2);

        evaluator.notifyFulfilled("all_tasks_completed");
        evaluator.notifyAbsent();
        evaluator.notifyFulfilled("all_tasks_completed");
        assertFalse(evaluator.shouldStop(StopEvaluationContext.builder().build()));

        evaluator.notifyFulfilled("all_tasks_completed");
        assertTrue(evaluator.shouldStop(StopEvaluationContext.builder().build()));
    }

    @Test
    @DisplayName("notify_absent clears fulfilled state and matched text")
    void testCompletionPromiseEvaluatorAbsentClearsState() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed", 2);

        evaluator.notifyFulfilled("all_tasks_completed");
        evaluator.notifyFulfilled("all_tasks_completed");
        assertTrue(evaluator.shouldStop(StopEvaluationContext.builder().build()));

        evaluator.notifyAbsent();

        assertFalse(evaluator.shouldStop(StopEvaluationContext.builder().build()));
        Map<String, Object> state = evaluator.getState();
        assertEquals(0, state.get("confirmation_count"));
        assertEquals("", state.get("matched_text"));
    }

    @Test
    @DisplayName("Rails can enqueue follow-up messages through the controller")
    void testTaskLoopControllerCanEnqueueFollowUp() {
        TaskLoopController controller = new TaskLoopController();

        controller.enqueueFollowUp("confirm completion");

        assertTrue(controller.hasFollowUp());
        assertEquals(List.of("confirm completion"), controller.drainFollowUp());
    }

    private static AgentCallbackContext ctxWithOutput(CompletionPromiseEvaluator evaluator, String output) {
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setResult(Map.of("output", output));
        return AgentCallbackContext.builder()
                .agent(new FakeAgent(evaluator))
                .inputs(inputs)
                .build();
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
