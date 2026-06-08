/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.FixLoopController;
import com.openjiuwen.auto_harness.infra.FixLoopResult;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for FixLoopController.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_fix_loop}.</p>
 */
@DisplayName("Fix Loop Controller Tests")
class TestFixLoop {

    @Nested
    @DisplayName("FixLoopResult Tests")
    class TestFixLoopResultClass {

        @Test
        @DisplayName("default values")
        void testDefaults() {
            FixLoopResult result = new FixLoopResult();

            assertFalse(result.isSuccess());
            assertEquals(0, result.getAttempts());
            assertEquals(1, result.getPhase());
            assertTrue(result.getErrorLog().isEmpty());
        }
    }

    @Nested
    @DisplayName("FixLoop Phase 1 Tests")
    class TestFixLoopPhase1 {

        @Test
        @DisplayName("pass on first attempt")
        void testPassFirstAttempt() {
            FixLoopController controller = new FixLoopController(3, 0, 1.0);

            FixLoopResult result = controller.run(
                    () -> new FixLoopController.CiResult(true, ""),
                    errors -> {
                    });

            assertTrue(result.isSuccess());
            assertEquals(1, result.getAttempts());
            assertEquals(1, result.getPhase());
        }

        @Test
        @DisplayName("pass after retries")
        void testPassAfterRetries() {
            AtomicInteger callCount = new AtomicInteger();
            FixLoopController controller = new FixLoopController(5, 0, 1.0);

            FixLoopResult result = controller.run(
                    () -> {
                        int count = callCount.incrementAndGet();
                        return new FixLoopController.CiResult(count >= 3, "lint error");
                    },
                    errors -> {
                    });

            assertTrue(result.isSuccess());
            assertEquals(3, result.getAttempts());
        }

        @Test
        @DisplayName("exhaust retries")
        void testExhaustRetries() {
            FixLoopController controller = new FixLoopController(2, 0, 1.0);

            FixLoopResult result = controller.run(
                    () -> new FixLoopController.CiResult(false, "fail"),
                    errors -> {
                    });

            assertFalse(result.isSuccess());
            assertEquals(2, result.getAttempts());
            assertEquals(2, result.getErrorLog().size());
        }

        @Test
        @DisplayName("ci timeout")
        void testCiTimeout() {
            FixLoopController controller = new FixLoopController(1, 0, 0.001);

            FixLoopResult result = controller.run(
                    () -> {
                        Thread.sleep(20);
                        return new FixLoopController.CiResult(true, "");
                    },
                    errors -> {
                    });

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorLog().get(0).toLowerCase().contains("timeout"));
        }
    }

    @Nested
    @DisplayName("FixLoop Phase 2 Tests")
    class TestFixLoopPhase2 {

        @Test
        @DisplayName("evaluator approves")
        void testEvaluatorApproves() {
            AtomicInteger callCount = new AtomicInteger();
            FixLoopController controller = new FixLoopController(1, 3, 1.0);

            FixLoopResult result = controller.run(
                    () -> new FixLoopController.CiResult(false, "err"),
                    errors -> {
                    },
                    () -> new FixLoopController.ReviewResult(callCount.incrementAndGet() >= 2));

            assertTrue(result.isSuccess());
            assertEquals(2, result.getPhase());
        }

        @Test
        @DisplayName("no evaluator skips phase 2")
        void testNoEvaluatorSkipsPhase2() {
            FixLoopController controller = new FixLoopController(1, 9, 1.0);

            FixLoopResult result = controller.run(
                    () -> new FixLoopController.CiResult(false, "err"),
                    errors -> {
                    },
                    null);

            assertFalse(result.isSuccess());
            assertEquals(1, result.getPhase());
        }
    }
}
