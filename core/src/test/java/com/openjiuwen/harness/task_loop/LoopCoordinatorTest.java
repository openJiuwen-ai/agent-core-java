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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code LoopCoordinator} in
 * {@code openjiuwen/harness/task_loop/loop_coordinator.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_loop_coordinator} in
 * {@code tests/unit_tests/harness/test_loop_coordinator.py}.</p>
 */
class LoopCoordinatorTest {

    @Test
    void testDefaults() {
        LoopCoordinator coordinator = new LoopCoordinator();
        assertEquals(0, coordinator.getCurrentIteration());
        assertFalse(coordinator.isAborted());
        assertTrue(coordinator.shouldContinue());
    }

    @Test
    void testIncrementIteration() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.reset();
        coordinator.incrementIteration();
        coordinator.incrementIteration();

        assertEquals(2, coordinator.getCurrentIteration());
    }

    @Test
    void testMaxIterationsStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new MaxRoundsEvaluator(2)));
        coordinator.reset();
        assertTrue(coordinator.shouldContinue());
        coordinator.incrementIteration();
        assertTrue(coordinator.shouldContinue());
        coordinator.incrementIteration();
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testMaxTokenUsageStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TokenBudgetEvaluator(100)));
        coordinator.reset();
        coordinator.addTokenUsage(50);
        assertTrue(coordinator.shouldContinue());
        coordinator.addTokenUsage(50);
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testAbortStopsImmediately() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.reset();
        coordinator.requestAbort();

        assertTrue(coordinator.isAborted());
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testTimeoutStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TimeoutEvaluator(0.0)));
        coordinator.reset();

        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testCustomPredicateStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new CustomPredicateEvaluator(ctx -> true)));
        coordinator.reset();

        assertFalse(coordinator.shouldContinue());
    }

    @Test
    void testCustomPredicateContinue() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new CustomPredicateEvaluator(ctx -> false)));
        coordinator.reset();

        assertTrue(coordinator.shouldContinue());
    }

    @Test
    void testResetClearsState() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new MaxRoundsEvaluator(10)));
        coordinator.reset();
        coordinator.incrementIteration();
        coordinator.addTokenUsage(999);
        coordinator.requestAbort();

        coordinator.reset();

        assertEquals(0, coordinator.getCurrentIteration());
        assertFalse(coordinator.isAborted());
        assertTrue(coordinator.shouldContinue());
    }

    @Test
    void testNegativeTokensIgnored() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TokenBudgetEvaluator(100)));
        coordinator.reset();
        coordinator.addTokenUsage(-50);
        coordinator.addTokenUsage(0);

        assertTrue(coordinator.shouldContinue());
    }
}
