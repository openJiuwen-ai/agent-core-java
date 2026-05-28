/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolCallOperator.
 * Mirrors Python's tests/unit_tests/core/operator/test_tool_call.py
 */
class TestToolCall {

    @Nested
    @DisplayName("ToolCall tests")
    class ToolCallTests {

        @Test
        @DisplayName("test operator id default")
        void testOperatorIdDefault() {
            // Test default operator_id.
            ToolCallOperator op = new ToolCallOperator();
            assertEquals("tool_call", op.getOperatorId());
        }

        @Test
        @DisplayName("test operator id custom")
        void testOperatorIdCustom() {
            // Test custom operator_id.
            ToolCallOperator op = new ToolCallOperator(null, "test_tool", null, null);
            assertEquals("test_tool", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables without tool registry")
        void testGetTunablesWithoutToolRegistry() {
            // Test getTunables returns empty without tool registry.
            ToolCallOperator op = new ToolCallOperator();
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.isEmpty());
        }

        @Test
        @DisplayName("test get state")
        void testGetState() {
            // Test getState returns enabled and tool descriptions.
            ToolCallOperator op = new ToolCallOperator();
            Map<String, Object> state = op.getState();

            assertTrue(state.containsKey("enabled"));
        }

        @Test
        @DisplayName("test set parameter enabled")
        void testSetParameterEnabled() {
            // Test setParameter for enabled.
            ToolCallOperator op = new ToolCallOperator();

            op.setParameter("enabled", false);
            assertFalse((Boolean) op.getState().get("enabled"));

            op.setParameter("enabled", true);
            assertTrue((Boolean) op.getState().get("enabled"));
        }

        @Test
        @DisplayName("test set parameter unknown target")
        void testSetParameterUnknownTarget() {
            // Test setParameter ignores unknown targets.
            ToolCallOperator op = new ToolCallOperator();

            // Should not raise exception
            op.setParameter("unknown", "value");
            // State should remain unchanged
            assertNotNull(op.getState());
        }
    }
}