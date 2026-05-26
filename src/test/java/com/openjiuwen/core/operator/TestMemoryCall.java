/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.memory_call.MemoryCallOperator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryCallOperator.
 * Mirrors Python's tests/unit_tests/core/operator/test_memory_call.py
 */
class TestMemoryCall {

    @Nested
    @DisplayName("MemoryCall tests")
    class MemoryCallTests {

        @Test
        @DisplayName("test operator id default")
        void testOperatorIdDefault() {
            // Test default operator_id.
            MemoryCallOperator op = new MemoryCallOperator();
            assertEquals("memory_call", op.getOperatorId());
        }

        @Test
        @DisplayName("test operator id custom")
        void testOperatorIdCustom() {
            // Test custom operator_id.
            MemoryCallOperator op = new MemoryCallOperator(null, "custom_memory", null);
            assertEquals("custom_memory", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables")
        void testGetTunables() {
            // Test getTunables returns enabled and max_retries.
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
            // Test tunable constraints are correctly set.
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
            // Test setParameter for enabled.
            MemoryCallOperator op = new MemoryCallOperator();

            op.setParameter("enabled", false);
            assertFalse((Boolean) op.getState().get("enabled"));

            op.setParameter("enabled", true);
            assertTrue((Boolean) op.getState().get("enabled"));
        }

        @Test
        @DisplayName("test set parameter max retries")
        void testSetParameterMaxRetries() {
            // Test setParameter for max_retries.
            MemoryCallOperator op = new MemoryCallOperator();

            op.setParameter("max_retries", 3);
            assertEquals(3, op.getState().get("max_retries"));
        }

        @Test
        @DisplayName("test set parameter max retries clamped")
        void testSetParameterMaxRetriesClamped() {
            // Test setParameter clamps max_retries to 0-5.
            MemoryCallOperator op = new MemoryCallOperator();

            op.setParameter("max_retries", 10);
            assertEquals(5, op.getState().get("max_retries"));

            op.setParameter("max_retries", -1);
            assertEquals(0, op.getState().get("max_retries"));
        }

        @Test
        @DisplayName("test get state")
        void testGetState() {
            // Test getState returns enabled and max_retries.
            MemoryCallOperator op = new MemoryCallOperator();
            Map<String, Object> state = op.getState();

            assertTrue(state.containsKey("enabled"));
            assertTrue(state.containsKey("max_retries"));
        }
    }
}