/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.operator;

import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.operator.memory_call.MemoryCallOperator;
import com.openjiuwen.core.operator.memory_call.MemoryOperation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

/**
 * Unit tests for MemoryCall operator.
 * 
 * <p>Mirrors Python's tests/unit_tests/core/operator/test_memory_call.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/core/operator/test_memory_call.py
 * 
 * <p>NOTE: Python's on_parameter_updated callback feature is not implemented in Java.
 * The TestMemoryCallOperatorCallbacks class tests are omitted as Java MemoryCallOperator
 * does not support callback functionality.
 */
@ExtendWith(MockitoExtension.class)
class TestMemoryCall {

    // ========== TestMemoryCallOperator tests ==========

    @Test
    @DisplayName("Test default operator_id")
    void testOperatorIdDefault() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        assertEquals("memory_call", operator.getOperatorId());
    }

    @Test
    @DisplayName("Test custom operator_id")
    void testOperatorIdCustom() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator op = new MemoryCallOperator(memory, "custom_memory", null);
        assertEquals("custom_memory", op.getOperatorId());
    }

    @Test
    @DisplayName("Test get_tunables returns enabled and max_retries")
    void testGetTunables() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        Map<String, TunableSpec> tunables = operator.getTunables();
        
        assertTrue(tunables.containsKey("enabled"));
        assertTrue(tunables.containsKey("max_retries"));
        assertEquals("discrete", tunables.get("enabled").kind());
        assertEquals("discrete", tunables.get("max_retries").kind());
    }

    @Test
    @DisplayName("Test tunable constraints are correctly set")
    void testGetTunablesConstraints() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        Map<String, TunableSpec> tunables = operator.getTunables();
        
        assertEquals(Map.of("type", "bool"), tunables.get("enabled").constraint());
        assertEquals(Map.of("type", "int", "min", 0, "max", 5), tunables.get("max_retries").constraint());
    }

    @Test
    @DisplayName("Test set_parameter for enabled")
    void testSetParameterEnabled() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        
        operator.setParameter("enabled", false);
        assertFalse((Boolean) operator.getState().get("enabled"));
        
        operator.setParameter("enabled", true);
        assertTrue((Boolean) operator.getState().get("enabled"));
    }

    @Test
    @DisplayName("Test set_parameter for max_retries")
    void testSetParameterMaxRetries() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        
        operator.setParameter("max_retries", 3);
        assertEquals(3, operator.getState().get("max_retries"));
    }

    @Test
    @DisplayName("Test set_parameter clamps max_retries to 0-5")
    void testSetParameterMaxRetriesClamped() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        
        operator.setParameter("max_retries", 10);
        assertEquals(5, operator.getState().get("max_retries"));
        
        operator.setParameter("max_retries", -1);
        assertEquals(0, operator.getState().get("max_retries"));
    }

    @Test
    @DisplayName("Test get_state returns enabled and max_retries")
    void testGetState() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        Map<String, Object> state = operator.getState();
        
        assertTrue(state.containsKey("enabled"));
        assertTrue(state.containsKey("max_retries"));
        assertTrue((Boolean) state.get("enabled"));
        assertEquals(0, state.get("max_retries"));
    }

    @Test
    @DisplayName("Test get_state with custom values")
    void testGetStateWithCustomValues() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator op = new MemoryCallOperator(memory);
        op.loadState(Map.of("enabled", false, "max_retries", 3));
        Map<String, Object> state = op.getState();
        
        assertFalse((Boolean) state.get("enabled"));
        assertEquals(3, state.get("max_retries"));
    }

    @Test
    @DisplayName("Test load_state restores state")
    void testLoadState() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        operator.loadState(Map.of("enabled", false, "max_retries", 2));
        Map<String, Object> state = operator.getState();
        
        assertFalse((Boolean) state.get("enabled"));
        assertEquals(2, state.get("max_retries"));
    }

    @Test
    @DisplayName("Test load_state with partial state")
    void testLoadStatePartial() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        operator.loadState(Map.of("enabled", false));
        Map<String, Object> state = operator.getState();
        
        assertFalse((Boolean) state.get("enabled"));
        assertEquals(0, state.get("max_retries"));
    }

    @Test
    @DisplayName("Test load_state clamps max_retries to 0-5")
    void testLoadStateClampedRetries() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator op = new MemoryCallOperator(memory);
        
        op.loadState(Map.of("max_retries", 10));
        assertEquals(5, op.getState().get("max_retries"));
        
        op.loadState(Map.of("max_retries", -1));
        assertEquals(0, op.getState().get("max_retries"));
    }

    @Test
    @DisplayName("Test set_parameter ignores unknown targets")
    void testSetParameterUnknownTarget() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator op = new MemoryCallOperator(memory);
        // Should not raise - just ignore
        assertDoesNotThrow(() -> op.setParameter("unknown", "value"));
        // State should remain unchanged for unknown keys
        assertTrue((Boolean) op.getState().get("enabled"));
    }
}