/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.harness.schema.CustomPredicateEvaluator;
import com.openjiuwen.harness.schema.MaxRoundsEvaluator;
import com.openjiuwen.harness.schema.TimeoutEvaluator;
import com.openjiuwen.harness.schema.TokenBudgetEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopCoordinatorTest {

    @Test
    void testDefaults() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.reset();
        assertTrue(coordinator.shouldContinue());
    }

    @Test
    void testMaxRoundsStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new MaxRoundsEvaluator(2)));
        coordinator.reset();
        assertTrue(coordinator.shouldContinue());
        coordinator.incrementIteration();
        assertTrue(coordinator.shouldContinue());
        coordinator.incrementIteration();
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testTokenBudgetStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TokenBudgetEvaluator(100)));
        coordinator.reset();
        coordinator.addTokenUsage(50);
        assertTrue(coordinator.shouldContinue());
        coordinator.addTokenUsage(50);
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testAbortAndTimeout() {
        LoopCoordinator aborted = new LoopCoordinator();
        aborted.reset();
        aborted.requestAbort();
        assertFalse(aborted.shouldContinue());

        LoopCoordinator timedOut = new LoopCoordinator(List.of(new TimeoutEvaluator(0.0)));
        timedOut.reset();
        assertFalse(timedOut.shouldContinue());
    }

    @Test
    void testCustomPredicate() {
        LoopCoordinator stop = new LoopCoordinator(List.of(new CustomPredicateEvaluator(ctx -> true)));
        stop.reset();
        assertFalse(stop.shouldContinue());

        LoopCoordinator cont = new LoopCoordinator(List.of(new CustomPredicateEvaluator(ctx -> false)));
        cont.reset();
        assertTrue(cont.shouldContinue());
    }
}
