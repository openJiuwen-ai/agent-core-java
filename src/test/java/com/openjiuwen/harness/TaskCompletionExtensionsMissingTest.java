/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.StopEvaluationContext;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.LoopQueues;
import com.openjiuwen.harness.task_loop.TaskLoopController;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/test_task_completion_extensions.py}.
 */
class TaskCompletionExtensionsMissingTest {

    @Test
    void promiseBlockCanIncludeEvidenceLines() {
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
    void taskCompletionRailRejectsDetailsByDefault() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "all_tasks_completed");
        ContextFixture fixture = contextWithOutput("""
                <promise>all_tasks_completed
                Completed tasks:
                - created output
                </promise>""");

        rail.afterTaskIteration(fixture.context());

        assertFalse(fixture.evaluator().shouldStop(new StopEvaluationContext()));
    }

    @Test
    void taskCompletionRailAcceptsDetailsWhenEnabled() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "all_tasks_completed", 1, true);
        ContextFixture fixture = contextWithOutput("""
                <promise>all_tasks_completed
                Completed tasks:
                - created output
                </promise>""");

        rail.afterTaskIteration(fixture.context());

        assertTrue(fixture.evaluator().shouldStop(new StopEvaluationContext()));
    }

    @Test
    void taskCompletionRailBuildsMultiConfirmationEvaluator() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "all_tasks_completed", 2, false);
        StopConditionEvaluator evaluator = rail.buildEvaluators().getFirst();
        CompletionPromiseEvaluator promiseEvaluator = assertInstanceOf(
                CompletionPromiseEvaluator.class,
                evaluator
        );

        promiseEvaluator.notifyFulfilled("all_tasks_completed");
        assertFalse(promiseEvaluator.shouldStop(new StopEvaluationContext()));
        promiseEvaluator.notifyFulfilled("all_tasks_completed");
        assertTrue(promiseEvaluator.shouldStop(new StopEvaluationContext()));
    }

    @Test
    void completionPromiseEvaluatorRequiresConsecutiveConfirmations() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed", 2);

        evaluator.notifyFulfilled("all_tasks_completed");
        evaluator.notifyAbsent();
        evaluator.notifyFulfilled("all_tasks_completed");
        assertFalse(evaluator.shouldStop(new StopEvaluationContext()));

        evaluator.notifyFulfilled("all_tasks_completed");
        assertTrue(evaluator.shouldStop(new StopEvaluationContext()));
    }

    @Test
    void completionPromiseEvaluatorAbsentClearsState() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed", 2);

        evaluator.notifyFulfilled("all_tasks_completed");
        evaluator.notifyFulfilled("all_tasks_completed");
        assertTrue(evaluator.shouldStop(new StopEvaluationContext()));

        evaluator.notifyAbsent();
        assertFalse(evaluator.shouldStop(new StopEvaluationContext()));
        Map<String, Object> state = evaluator.getState();
        assertNotNull(state);
        assertEquals(0, state.get("confirmation_count"));
        assertEquals("", state.get("matched_text"));
    }

    @Test
    void taskLoopControllerCanEnqueueFollowUp() {
        TaskLoopController controller = new TaskLoopController();
        LoopQueues queues = new LoopQueues();

        controller.enqueueFollowUp("confirm completion");
        queues.pushFollowUp("confirm completion");

        assertTrue(controller.hasFollowUp());
        assertEquals(List.of("confirm completion"), controller.drainFollowUp());
        assertEquals(List.of("confirm completion"), queues.drainFollowUp());
    }

    private static ContextFixture contextWithOutput(String output) {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("all_tasks_completed");
        DeepAgent agent = new CoordinatorAgent(evaluator);
        CallbackContext context = new CallbackContext(agent, Map.of(
                "result", Map.of("output", output)
        ));
        return new ContextFixture(context, evaluator);
    }

    private record ContextFixture(CallbackContext context, CompletionPromiseEvaluator evaluator) {
    }

    private static final class CoordinatorAgent extends DeepAgent {

        private final LoopCoordinator coordinator;

        private CoordinatorAgent(CompletionPromiseEvaluator evaluator) {
            super(new AgentCard("deep", "deep", "test"));
            this.coordinator = new LoopCoordinator(List.of(evaluator));
        }

        @Override
        public LoopCoordinator loopCoordinator() {
            return coordinator;
        }
    }
}
