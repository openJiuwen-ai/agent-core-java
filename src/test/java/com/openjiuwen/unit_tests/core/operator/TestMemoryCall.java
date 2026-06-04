/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.operator;

import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.operator.memory_call.MemoryCallOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MemoryCallOperator.
 *
 * <p>Mirrors Python's tests/unit_tests/core/operator/test_memory_call.py.</p>
 */
class TestMemoryCall {

    @Nested
    @DisplayName("MemoryCall tests")
    class MemoryCallTests {

        @Test
        @DisplayName("test operator id default")
        void testOperatorIdDefault() {
            MemoryCallOperator op = new MemoryCallOperator();
            assertEquals("memory_call", op.getOperatorId());
        }

        @Test
        @DisplayName("test operator id custom")
        void testOperatorIdCustom() {
            MemoryCallOperator op = new MemoryCallOperator(null, "custom_memory", null);
            assertEquals("custom_memory", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables")
        void testGetTunables() {
            MemoryCallOperator op = new MemoryCallOperator();
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("enabled"));
            assertTrue(tunables.containsKey("max_retries"));
            assertEquals("discrete", tunables.get("enabled").kind());
            assertEquals("discrete", tunables.get("max_retries").kind());
        }

        @Test
        @DisplayName("test get tunables constraints")
        void testGetTunablesConstraints() {
            MemoryCallOperator op = new MemoryCallOperator();
            Map<String, TunableSpec> tunables = op.getTunables();

            @SuppressWarnings("unchecked")
            Map<String, Object> enabledConstraint = (Map<String, Object>) tunables.get("enabled").constraint();
            assertEquals("bool", enabledConstraint.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> retriesConstraint = (Map<String, Object>) tunables.get("max_retries").constraint();
            assertEquals("int", retriesConstraint.get("type"));
            assertEquals(0, retriesConstraint.get("min"));
            assertEquals(5, retriesConstraint.get("max"));
        }

        @Test
        @DisplayName("test set parameter enabled")
        void testSetParameterEnabled() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.setParameter("enabled", false);
            assertFalse((Boolean) op.getState().get("enabled"));

            op.setParameter("enabled", true);
            assertTrue((Boolean) op.getState().get("enabled"));
        }

        @Test
        @DisplayName("test set parameter max retries")
        void testSetParameterMaxRetries() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.setParameter("max_retries", 3);
            assertEquals(3, op.getState().get("max_retries"));
        }

        @Test
        @DisplayName("test set parameter max retries clamped")
        void testSetParameterMaxRetriesClamped() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.setParameter("max_retries", 10);
            assertEquals(5, op.getState().get("max_retries"));

            op.setParameter("max_retries", -1);
            assertEquals(0, op.getState().get("max_retries"));
        }

        @Test
        @DisplayName("test get state")
        void testGetState() {
            MemoryCallOperator op = new MemoryCallOperator();
            Map<String, Object> state = op.getState();

            assertEquals(true, state.get("enabled"));
            assertEquals(0, state.get("max_retries"));
        }

        @Test
        @DisplayName("test get state with custom values")
        void testGetStateWithCustomValues() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.loadState(Map.of("enabled", false, "max_retries", 3));
            Map<String, Object> state = op.getState();

            assertEquals(false, state.get("enabled"));
            assertEquals(3, state.get("max_retries"));
        }

        @Test
        @DisplayName("test load state")
        void testLoadState() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.loadState(Map.of("enabled", false, "max_retries", 2));
            Map<String, Object> state = op.getState();

            assertEquals(false, state.get("enabled"));
            assertEquals(2, state.get("max_retries"));
        }

        @Test
        @DisplayName("test load state partial")
        void testLoadStatePartial() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.loadState(Map.of("enabled", false));
            Map<String, Object> state = op.getState();

            assertEquals(false, state.get("enabled"));
            assertEquals(0, state.get("max_retries"));
        }

        @Test
        @DisplayName("test load state clamped retries")
        void testLoadStateClampedRetries() {
            MemoryCallOperator op = new MemoryCallOperator();

            op.loadState(Map.of("max_retries", 10));
            assertEquals(5, op.getState().get("max_retries"));

            op.loadState(Map.of("max_retries", -1));
            assertEquals(0, op.getState().get("max_retries"));
        }

        @Test
        @DisplayName("test set parameter triggers callback")
        void testSetParameterTriggersCallback() {
            List<List<Object>> calls = new ArrayList<>();
            MemoryCallOperator op = new MemoryCallOperator(
                    null, "memory_call", null, (target, value) -> calls.add(List.of(target, value)));

            op.setParameter("enabled", false);

            assertEquals(List.of(List.of("enabled", false)), calls);
        }

        @Test
        @DisplayName("test set parameter max retries triggers callback")
        void testSetParameterMaxRetriesTriggersCallback() {
            List<List<Object>> calls = new ArrayList<>();
            MemoryCallOperator op = new MemoryCallOperator(
                    null, "memory_call", null, (target, value) -> calls.add(List.of(target, value)));

            op.setParameter("max_retries", 3);

            assertEquals(List.of(List.of("max_retries", 3)), calls);
        }

        @Test
        @DisplayName("test load state triggers callback")
        void testLoadStateTriggersCallback() {
            List<List<Object>> calls = new ArrayList<>();
            MemoryCallOperator op = new MemoryCallOperator(
                    null, "memory_call", null, (target, value) -> calls.add(List.of(target, value)));

            op.loadState(Map.of("enabled", false, "max_retries", 2));

            assertEquals(2, calls.size());
            assertTrue(calls.contains(List.of("enabled", false)));
            assertTrue(calls.contains(List.of("max_retries", 2)));
        }

        @Test
        @DisplayName("test set parameter unknown target")
        void testSetParameterUnknownTarget() {
            List<List<Object>> calls = new ArrayList<>();
            MemoryCallOperator op = new MemoryCallOperator(
                    null, "memory_call", null, (target, value) -> calls.add(List.of(target, value)));

            assertDoesNotThrow(() -> op.setParameter("unknown", "value"));
            assertTrue(calls.isEmpty());
        }
    }
}
