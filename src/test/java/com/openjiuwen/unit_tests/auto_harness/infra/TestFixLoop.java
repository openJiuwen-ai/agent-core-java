/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FixLoopController.
 * <p>
 * Mirrors Python's test_fix_loop.py from
 * <code>tests/unit_tests/auto_harness/infra/test_fix_loop.py</code>.
 */
@DisplayName("Fix Loop Controller Tests")
class TestFixLoop {

    // Stub classes for testing
    static class CIStub {
        final boolean passed;
        final String errors;

        CIStub(boolean passed, String errors) {
            this.passed = passed;
            this.errors = errors;
        }
    }

    static class ReviewStub {
        final boolean approved;

        ReviewStub(boolean approved) {
            this.approved = approved;
        }
    }

    static class FixLoopResult {
        boolean success = false;
        int attempts = 0;
        int phase = 1;
        List<String> errorLog = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public int getAttempts() { return attempts; }
        public int getPhase() { return phase; }
        public List<String> getErrorLog() { return errorLog; }
    }

    static class FixLoopController {
        private final int phase1MaxRetries;
        private final int phase2MaxRetries;
        private final long timeoutPerAttemptMs;

        public FixLoopController(int phase1MaxRetries, int phase2MaxRetries, long timeoutPerAttemptMs) {
            this.phase1MaxRetries = phase1MaxRetries;
            this.phase2MaxRetries = phase2MaxRetries;
            this.timeoutPerAttemptMs = timeoutPerAttemptMs;
        }

        public FixLoopController(int phase1MaxRetries) {
            this(phase1MaxRetries, 0, 0);
        }

        public FixLoopResult run(
            java.util.function.Supplier<CompletableFuture<CIStub>> ciSupplier,
            java.util.function.Function<String, CompletableFuture<Void>> fixer
        ) {
            FixLoopResult result = new FixLoopResult();
            for (int i = 0; i < phase1MaxRetries; i++) {
                result.attempts++;
                try {
                    CIStub ci = ciSupplier.get().get(timeoutPerAttemptMs > 0 ? timeoutPerAttemptMs : 10000, TimeUnit.MILLISECONDS);
                    if (ci.passed) {
                        result.success = true;
                        return result;
                    }
                    result.errorLog.add(ci.errors);
                    fixer.apply(ci.errors).get();
                } catch (TimeoutException e) {
                    result.errorLog.add("timeout");
                } catch (Exception e) {
                    result.errorLog.add(e.getMessage());
                }
            }
            return result;
        }
    }

    @Nested
    @DisplayName("FixLoopResult Tests")
    class TestFixLoopResultClass {

        @Test
        @DisplayName("default values")
        void testDefaults() {
            FixLoopResult r = new FixLoopResult();
            assertFalse(r.isSuccess());
            assertEquals(0, r.getAttempts());
            assertEquals(1, r.getPhase());
            assertTrue(r.getErrorLog().isEmpty());
        }
    }

    @Nested
    @DisplayName("FixLoop Phase 1 Tests")
    class TestFixLoopPhase1 {

        @Test
        @DisplayName("pass on first attempt")
        void testPassFirstAttempt() throws Exception {
            FixLoopController ctrl = new FixLoopController(3);

            CompletableFuture<CIStub> ci = CompletableFuture.completedFuture(new CIStub(true, ""));
            java.util.function.Function<String, CompletableFuture<Void>> fixer = (errors) -> CompletableFuture.completedFuture(null);

            FixLoopResult result = ctrl.run(() -> ci, fixer);
            assertTrue(result.isSuccess());
            assertEquals(1, result.getAttempts());
            assertEquals(1, result.getPhase());
        }

        @Test
        @DisplayName("pass after retries")
        void testPassAfterRetries() throws Exception {
            int[] callCount = {0};

            FixLoopController ctrl = new FixLoopController(5);
            java.util.function.Supplier<CompletableFuture<CIStub>> ciSupplier = () -> {
                callCount[0]++;
                boolean passed = callCount[0] >= 3;
                return CompletableFuture.completedFuture(new CIStub(passed, "lint error"));
            };
            java.util.function.Function<String, CompletableFuture<Void>> fixer = (errors) -> CompletableFuture.completedFuture(null);

            FixLoopResult result = ctrl.run(ciSupplier, fixer);
            assertTrue(result.isSuccess());
            assertEquals(3, result.getAttempts());
        }

        @Test
        @DisplayName("exhaust retries")
        void testExhaustRetries() throws Exception {
            FixLoopController ctrl = new FixLoopController(2, 0, 0);

            java.util.function.Supplier<CompletableFuture<CIStub>> ciSupplier = () ->
                CompletableFuture.completedFuture(new CIStub(false, "fail"));
            java.util.function.Function<String, CompletableFuture<Void>> fixer = (errors) -> CompletableFuture.completedFuture(null);

            FixLoopResult result = ctrl.run(ciSupplier, fixer);
            assertFalse(result.isSuccess());
            assertEquals(2, result.getAttempts());
            assertEquals(2, result.getErrorLog().size());
        }

        @Test
        @DisplayName("ci timeout")
        void testCiTimeout() throws Exception {
            FixLoopController ctrl = new FixLoopController(1, 0, 10); // 10ms timeout

            java.util.function.Supplier<CompletableFuture<CIStub>> ciSupplier = () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10000); // Sleep longer than timeout
                    } catch (InterruptedException e) {}
                    return new CIStub(true, "");
                });
            java.util.function.Function<String, CompletableFuture<Void>> fixer = (errors) -> CompletableFuture.completedFuture(null);

            FixLoopResult result = ctrl.run(ciSupplier, fixer);
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorLog().contains("timeout"));
        }
    }
}