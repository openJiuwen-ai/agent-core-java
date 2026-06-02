/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.schema.CustomPredicateEvaluator;
import com.openjiuwen.harness.schema.MaxRoundsEvaluator;
import com.openjiuwen.harness.schema.TimeoutEvaluator;
import com.openjiuwen.harness.schema.TokenBudgetEvaluator;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_loop_coordinator} in
 * {@code tests.unit_tests.harness.test_loop_coordinator}.
 */
class TestLoopCoordinator {

    @Test
    @Tag("level0")
    @DisplayName("defaults start at iteration zero")
    void testDefaults() {
        LoopCoordinator coordinator = new LoopCoordinator();
        assertEquals(0, coordinator.getCurrentIteration());
        assertFalse(coordinator.isAborted());
        assertTrue(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("incrementIteration advances the counter")
    void testIncrementIteration() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.reset();
        coordinator.incrementIteration();
        coordinator.incrementIteration();
        assertEquals(2, coordinator.getCurrentIteration());
    }

    @Test
    @Tag("level0")
    @DisplayName("max rounds evaluator stops at configured iteration")
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
    @Tag("level0")
    @DisplayName("token budget evaluator stops when budget is exhausted")
    void testMaxTokenUsageStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TokenBudgetEvaluator(100)));
        coordinator.reset();
        coordinator.addTokenUsage(50);
        assertTrue(coordinator.shouldContinue());
        coordinator.addTokenUsage(50);
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("abort stops immediately")
    void testAbortStopsImmediately() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.reset();
        coordinator.requestAbort();
        assertTrue(coordinator.isAborted());
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("timeout evaluator can stop immediately")
    void testTimeoutStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TimeoutEvaluator(0.0)));
        coordinator.reset();
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("custom predicate can stop the loop")
    void testCustomPredicateStop() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new CustomPredicateEvaluator(ctx -> true)));
        coordinator.reset();
        assertFalse(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("custom predicate can allow the loop to continue")
    void testCustomPredicateContinue() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new CustomPredicateEvaluator(ctx -> false)));
        coordinator.reset();
        assertTrue(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("reset clears iteration token usage and abort state")
    void testResetClearsState() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new MaxRoundsEvaluator(10)));
        coordinator.reset();
        coordinator.incrementIteration();
        coordinator.addTokenUsage(999);
        coordinator.requestAbort();

        coordinator.reset();
        assertEquals(0, coordinator.getCurrentIteration());
        assertEquals(0, coordinator.getTokenUsage());
        assertFalse(coordinator.isAborted());
        assertTrue(coordinator.shouldContinue());
    }

    @Test
    @Tag("level0")
    @DisplayName("negative tokens are ignored")
    void testNegativeTokensIgnored() {
        LoopCoordinator coordinator = new LoopCoordinator(List.of(new TokenBudgetEvaluator(100)));
        coordinator.reset();
        coordinator.addTokenUsage(-50);
        coordinator.addTokenUsage(0);
        assertTrue(coordinator.shouldContinue());
        assertEquals(0, coordinator.getTokenUsage());
    }
}
