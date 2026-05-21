/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionBudgetController.
 * <p>
 * Mirrors Python's test_session_budget.py from
 * <code>tests/unit_tests/auto_harness/infra/test_session_budget.py</code>.
 */
@DisplayName("Session Budget Tests")
class TestSessionBudget {

    // SessionBudgetController class mirroring Python's behavior
    static class SessionBudgetController {
        private double wallClockSecs;
        private double costLimitUsd;
        private double taskTimeoutSecs;
        private long startTimeNanos;
        private double currentCostUsd;

        public SessionBudgetController() {
            this.wallClockSecs = 0;
            this.costLimitUsd = 0;
            this.taskTimeoutSecs = 0;
            this.currentCostUsd = 0;
        }

        public SessionBudgetController(double wallClockSecs, double costLimitUsd, double taskTimeoutSecs) {
            this.wallClockSecs = wallClockSecs;
            this.costLimitUsd = costLimitUsd;
            this.taskTimeoutSecs = taskTimeoutSecs;
            this.currentCostUsd = 0;
        }

        public void start() {
            this.startTimeNanos = System.nanoTime();
        }

        public double elapsedSecs() {
            if (startTimeNanos == 0) return 0.0;
            return (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
        }

        public double remainingSecs() {
            return wallClockSecs - elapsedSecs();
        }

        public boolean shouldStop() {
            if (wallClockSecs > 0 && elapsedSecs() >= wallClockSecs) return true;
            if (costLimitUsd > 0 && currentCostUsd >= costLimitUsd) return true;
            return false;
        }

        public void addCost(double cost) {
            this.currentCostUsd += cost;
        }

        public double remainingCostUsd() {
            return Math.max(0, costLimitUsd - currentCostUsd);
        }

        public boolean checkTaskBudget() {
            if (taskTimeoutSecs > 0 && elapsedSecs() + taskTimeoutSecs > wallClockSecs) return false;
            return true;
        }

        // For testing: backdate start time
        public void setStartTimeNanos(long nanos) {
            this.startTimeNanos = nanos;
        }
    }

    @Nested
    @DisplayName("SessionBudgetController Tests")
    class TestSessionBudgetController {

        @Test
        @DisplayName("initial values")
        void testInitialValues() {
            SessionBudgetController ctrl = new SessionBudgetController();
            assertEquals(0.0, ctrl.elapsedSecs());
            assertFalse(ctrl.shouldStop());
        }

        @Test
        @DisplayName("remaining before start")
        void testRemainingBeforeStart() {
            SessionBudgetController ctrl = new SessionBudgetController(100.0, 0, 0);
            assertEquals(100.0, ctrl.remainingSecs());
        }

        @Test
        @DisplayName("start records time")
        void testStartRecordsTime() {
            SessionBudgetController ctrl = new SessionBudgetController(100.0, 0, 0);
            ctrl.start();
            assertTrue(ctrl.elapsedSecs() >= 0.0);
            assertTrue(ctrl.remainingSecs() <= 100.0);
        }

        @Test
        @DisplayName("wall clock exceeded")
        void testWallClockExceeded() {
            SessionBudgetController ctrl = new SessionBudgetController(10.0, 0, 0);
            ctrl.start();
            // Simulate time passing by backdating start
            ctrl.setStartTimeNanos(System.nanoTime() - 11_000_000_000L);
            assertTrue(ctrl.shouldStop());
        }

        @Test
        @DisplayName("cost exceeded")
        void testCostExceeded() {
            SessionBudgetController ctrl = new SessionBudgetController(0, 1.0, 0);
            ctrl.start();
            ctrl.addCost(1.5);
            assertTrue(ctrl.shouldStop());
        }

        @Test
        @DisplayName("cost not exceeded")
        void testCostNotExceeded() {
            SessionBudgetController ctrl = new SessionBudgetController(0, 10.0, 0);
            ctrl.start();
            ctrl.addCost(0.5);
            assertFalse(ctrl.shouldStop());
        }

        @Test
        @DisplayName("remaining cost")
        void testRemainingCost() {
            SessionBudgetController ctrl = new SessionBudgetController(0, 5.0, 0);
            ctrl.addCost(2.0);
            assertEquals(3.0, ctrl.remainingCostUsd());
        }

        @Test
        @DisplayName("remaining cost when exceeded")
        void testRemainingCostExceeded() {
            SessionBudgetController ctrl = new SessionBudgetController(0, 1.0, 0);
            ctrl.addCost(5.0);
            assertEquals(0.0, ctrl.remainingCostUsd());
        }

        @Test
        @DisplayName("check task budget sufficient")
        void testCheckTaskBudgetSufficient() {
            SessionBudgetController ctrl = new SessionBudgetController(3600.0, 0, 600.0);
            ctrl.start();
            assertTrue(ctrl.checkTaskBudget());
        }

        @Test
        @DisplayName("check task budget insufficient")
        void testCheckTaskBudgetInsufficient() {
            SessionBudgetController ctrl = new SessionBudgetController(100.0, 0, 600.0);
            ctrl.start();
            ctrl.setStartTimeNanos(System.nanoTime() - 90_000_000_000L);
            assertFalse(ctrl.checkTaskBudget());
        }
    }
}