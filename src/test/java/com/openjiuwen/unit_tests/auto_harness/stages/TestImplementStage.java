/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Implement stage helpers.
 * <p>
 * Mirrors Python's test_implement_stage.py from
 * <code>tests/unit_tests/auto_harness/stages/test_implement_stage.py</code>.
 */
@DisplayName("Implement Stage Tests")
class TestImplementStage {

    // Stub classes
    static class OutputSchemaStub {
        String type;
        int index;
        Map<String, Object> payload;

        OutputSchemaStub(String type, int index, Map<String, Object> payload) {
            this.type = type;
            this.index = index;
            this.payload = payload;
        }
    }

    static class FixLoopResultStub {
        boolean success;
        List<String> errorLog;

        FixLoopResultStub(boolean success, List<String> errorLog) {
            this.success = success;
            this.errorLog = errorLog;
        }
    }

    static class CIGateResultStub {
        boolean passed;
        List<Map<String, Object>> gates;
        String errors;

        CIGateResultStub(boolean passed, List<Map<String, Object>> gates, String errors) {
            this.passed = passed;
            this.gates = gates;
            this.errors = errors;
        }
    }

    static class FakeFixLoop {
        CompletableFuture<FixLoopResultStub> run(
            java.util.function.Supplier<CompletableFuture<CIGateResultStub>> ciRunner,
            java.util.function.Function<String, CompletableFuture<Void>> agentFixer
        ) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    CIGateResultStub ciResult = ciRunner.get().get();
                    agentFixer.apply(ciResult.errors).get();
                } catch (Exception e) {
                    // Ignore for stub
                }
                List<String> errorLog = new ArrayList<>();
                errorLog.add("Phase 1 failed");
                return new FixLoopResultStub(false, errorLog);
            });
        }
    }

    static class FakeCIGate {
        CompletableFuture<CIGateResultStub> run(String action) {
            List<Map<String, Object>> gates = new ArrayList<>();
            Map<String, Object> gate = new HashMap<>();
            gate.put("name", "lint");
            gate.put("passed", false);
            gate.put("output", "E501 line too long");
            gates.add(gate);

            return CompletableFuture.completedFuture(
                new CIGateResultStub(false, gates, "[lint]\nE501 line too long")
            );
        }
    }

    @Nested
    @DisplayName("Fix Loop Tests")
    class TestFixLoop {

        @Test
        @DisplayName("fix loop runs ci and fixer")
        void testFixLoopRunsCiAndFixer() throws Exception {
            FakeFixLoop fixLoop = new FakeFixLoop();
            FakeCIGate ciGate = new FakeCIGate();

            boolean[] fixerCalled = {false};
            java.util.function.Function<String, CompletableFuture<Void>> fixer = (errors) -> {
                fixerCalled[0] = true;
                return CompletableFuture.completedFuture(null);
            };

            FixLoopResultStub result = fixLoop.run(
                () -> ciGate.run("all"),
                fixer
            ).get();

            assertFalse(result.success);
            assertTrue(fixerCalled[0]);
            assertEquals(1, result.errorLog.size());
        }
    }

    @Nested
    @DisplayName("CI Gate Tests")
    class TestCIGate {

        @Test
        @DisplayName("ci gate returns lint failure")
        void testCiGateReturnsLintFailure() throws Exception {
            FakeCIGate ciGate = new FakeCIGate();
            CIGateResultStub result = ciGate.run("all").get();

            assertFalse(result.passed);
            assertEquals(1, result.gates.size());
            assertTrue(result.errors.contains("E501"));
        }
    }

    @Nested
    @DisplayName("Output Schema Tests")
    class TestOutputSchema {

        @Test
        @DisplayName("message output schema creation")
        void testMessageOutputSchemaCreation() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", "test message");
            OutputSchemaStub msg = new OutputSchemaStub("message", 0, payload);

            assertEquals("message", msg.type);
            assertEquals(0, msg.index);
            assertEquals("test message", msg.payload.get("content"));
        }
    }
}