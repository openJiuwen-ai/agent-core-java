/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StopConditionTest {

    @Test
    void maxRoundsStopsAtThreshold() {
        MaxRoundsEvaluator evaluator = new MaxRoundsEvaluator(2);

        assertFalse(evaluator.shouldStop(new StopEvaluationContext(1, 0, 0.0, null, Map.of())));
        assertTrue(evaluator.shouldStop(new StopEvaluationContext(2, 0, 0.0, null, Map.of())));
    }

    @Test
    void tokenBudgetStopsAtThreshold() {
        TokenBudgetEvaluator evaluator = new TokenBudgetEvaluator(100);

        assertFalse(evaluator.shouldStop(new StopEvaluationContext(0, 99, 0.0, null, Map.of())));
        assertTrue(evaluator.shouldStop(new StopEvaluationContext(0, 100, 0.0, null, Map.of())));
    }

    @Test
    void timeoutStopsAtThreshold() {
        TimeoutEvaluator evaluator = new TimeoutEvaluator(0.5);

        assertFalse(evaluator.shouldStop(new StopEvaluationContext(0, 0, 0.49, null, Map.of())));
        assertTrue(evaluator.shouldStop(new StopEvaluationContext(0, 0, 0.5, null, Map.of())));
    }

    @Test
    void completionPromiseNeedsConfiguredConfirmations() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("done", 2);

        evaluator.notifyFulfilled("done");
        assertFalse(evaluator.shouldStop(new StopEvaluationContext()));
        evaluator.notifyFulfilled("done");
        assertTrue(evaluator.shouldStop(new StopEvaluationContext()));
        evaluator.notifyAbsent();
        assertFalse(evaluator.shouldStop(new StopEvaluationContext()));
    }

    @Test
    void completionPromiseStateRoundTrips() {
        CompletionPromiseEvaluator evaluator = new CompletionPromiseEvaluator("done", 2);
        evaluator.notifyFulfilled("done");
        evaluator.notifyFulfilled("done");

        CompletionPromiseEvaluator restored = new CompletionPromiseEvaluator("done", 1);
        restored.loadState(evaluator.getState());

        assertTrue(restored.shouldStop(new StopEvaluationContext()));
    }

    @Test
    void customPredicateDelegatesToFunction() {
        CustomPredicateEvaluator evaluator = new CustomPredicateEvaluator(ctx -> ctx.getIteration() > 0);

        assertFalse(evaluator.shouldStop(new StopEvaluationContext()));
        assertTrue(evaluator.shouldStop(new StopEvaluationContext(1, 0, 0.0, null, Map.of())));
    }
}
