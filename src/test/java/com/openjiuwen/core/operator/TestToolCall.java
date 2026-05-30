/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        @DisplayName("test operator id")
        void testOperatorId() {
            ToolCallOperator op = new ToolCallOperator("test_tool");
            assertEquals("test_tool", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables without descriptions")
        void testGetTunablesWithoutDescriptions() {
            // Test get_tunables returns empty without descriptions.
            ToolCallOperator op = new ToolCallOperator("test_tool");
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.isEmpty());
        }

        @Test
        @DisplayName("test get tunables with descriptions")
        void testGetTunablesWithDescriptions() {
            ToolCallOperator op = new ToolCallOperator(
                    "test_tool",
                    Map.of("tool1", "Description 1", "tool2", "Description 2"));
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("tool_description"));
            assertEquals("text", tunables.get("tool_description").kind());
        }

        @Test
        @DisplayName("test set parameter tool description")
        void testSetParameterToolDescription() {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<String> targetRef = new AtomicReference<>();
            AtomicReference<Object> valueRef = new AtomicReference<>();
            ToolCallOperator op = new ToolCallOperator("test_tool", null, (target, value) -> {
                calls.incrementAndGet();
                targetRef.set(target);
                valueRef.set(value);
            });

            Map<String, String> descriptions = new LinkedHashMap<>();
            descriptions.put("tool1", "Updated description 1");
            descriptions.put("tool2", "Updated description 2");
            op.setParameter("tool_description", descriptions);

            assertEquals(1, calls.get());
            assertEquals("tool_description", targetRef.get());
            assertEquals(descriptions, valueRef.get());
        }

        @Test
        @DisplayName("test set parameter unknown target")
        void testSetParameterUnknownTarget() {
            // Test setParameter ignores unknown targets.
            AtomicInteger calls = new AtomicInteger();
            ToolCallOperator op = new ToolCallOperator("test_tool", null, (target, value) -> calls.incrementAndGet());

            op.setParameter("unknown", "value");
            assertEquals(0, calls.get());
        }

        @Test
        @DisplayName("test set parameter invalid value")
        void testSetParameterInvalidValue() {
            AtomicInteger calls = new AtomicInteger();
            ToolCallOperator op = new ToolCallOperator("test_tool", null, (target, value) -> calls.incrementAndGet());

            op.setParameter("tool_description", "not a dict");
            assertEquals(0, calls.get());
        }

        @Test
        @DisplayName("test get state")
        void testGetState() {
            ToolCallOperator op = new ToolCallOperator("test_tool", Map.of("tool1", "Description 1"));

            Map<String, Object> state = op.getState();

            assertTrue(state.containsKey("tool_description"));
            assertEquals(Map.of("tool1", "Description 1"), state.get("tool_description"));
        }

        @Test
        @DisplayName("test load state")
        void testLoadState() {
            ToolCallOperator op = new ToolCallOperator("test_tool");

            op.loadState(Map.of("tool_description", Map.of("tool1", "loaded desc")));

            assertEquals(Map.of("tool1", "loaded desc"), op.getState().get("tool_description"));
        }

        @Test
        @DisplayName("test set parameter triggers callback")
        void testSetParameterTriggersCallback() {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<Object> valueRef = new AtomicReference<>();
            ToolCallOperator op = new ToolCallOperator("test_tool", null, (target, value) -> {
                calls.incrementAndGet();
                valueRef.set(value);
            });

            op.setParameter("tool_description", Map.of("tool1", "new desc"));

            assertEquals(1, calls.get());
            assertEquals(Map.of("tool1", "new desc"), valueRef.get());
        }

        @Test
        @DisplayName("test load state triggers callback")
        void testLoadStateTriggersCallback() {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<Object> valueRef = new AtomicReference<>();
            ToolCallOperator op = new ToolCallOperator("test_tool", null, (target, value) -> {
                calls.incrementAndGet();
                valueRef.set(value);
            });

            op.loadState(Map.of("tool_description", Map.of("tool1", "loaded desc")));

            assertEquals(1, calls.get());
            assertEquals(Map.of("tool1", "loaded desc"), valueRef.get());
        }
    }
}
