/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.operator.memory_call;

import com.openjiuwen.core.operator.OperatorTestSupport;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Port of Python MemoryCallOperator tests.
 */
class MemoryCallOperatorTest {

    @Test
    @DisplayName("operator id, tunables and state")
    void testMetadataAndState() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        MemoryCallOperator custom = new MemoryCallOperator(memory, "custom_memory", null);

        assertEquals("memory_call", operator.getOperatorId());
        assertEquals("custom_memory", custom.getOperatorId());

        Map<String, TunableSpec> tunables = operator.getTunables();
        assertEquals("discrete", tunables.get("enabled").kind());
        assertEquals("discrete", tunables.get("max_retries").kind());
        assertEquals(Map.of("type", "bool"), tunables.get("enabled").constraint());
        assertEquals(Map.of("type", "int", "min", 0, "max", 5), tunables.get("max_retries").constraint());

        assertTrue((Boolean) operator.getState().get("enabled"));
        assertEquals(0, operator.getState().get("max_retries"));

        operator.setParameter("enabled", false);
        operator.setParameter("max_retries", 3);
        assertFalse((Boolean) operator.getState().get("enabled"));
        assertEquals(3, operator.getState().get("max_retries"));

        operator.setParameter("max_retries", 10);
        assertEquals(5, operator.getState().get("max_retries"));
        operator.setParameter("max_retries", -1);
        assertEquals(0, operator.getState().get("max_retries"));

        operator.loadState(Map.of("enabled", true, "max_retries", 2));
        assertTrue((Boolean) operator.getState().get("enabled"));
        assertEquals(2, operator.getState().get("max_retries"));

        operator.loadState(Map.of("max_retries", 10));
        assertEquals(5, operator.getState().get("max_retries"));
        operator.loadState(Map.of("max_retries", -1));
        assertEquals(0, operator.getState().get("max_retries"));
    }

    @Test
    @DisplayName("invoke basic, kwargs and empty inputs")
    void testInvokeBasicAndKwargs() throws Exception {
        MemoryOperation memory = mock(MemoryOperation.class);
        when(memory.invoke(anyMap(), anyMap())).thenReturn(Map.of("retrieved", "data"));
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        Object result = operator.invoke(Map.of("query", "test query"), session, Map.of("extra_param", "value"));
        assertEquals(Map.of("retrieved", "data"), result);
        verify(memory).invoke(Map.of("query", "test query"), Map.of("extra_param", "value"));
        assertEquals(Arrays.asList("memory_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());

        operator.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of());
        verify(memory, times(2)).invoke(anyMap(), anyMap());
    }

    @Test
    @DisplayName("invoke handles disabled, missing memory and retries")
    void testInvokeDisabledMissingAndRetryFailure() throws Exception {
        MemoryOperation memory = mock(MemoryOperation.class);
        when(memory.invoke(anyMap(), anyMap())).thenThrow(new IllegalArgumentException("error"));

        MemoryCallOperator operator = new MemoryCallOperator(memory);
        operator.setParameter("enabled", false);
        IllegalStateException disabled = assertThrows(
                IllegalStateException.class,
                () -> operator.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
        assertTrue(disabled.getMessage().contains("disabled"));

        MemoryCallOperator missing = new MemoryCallOperator();
        IllegalStateException noMemory = assertThrows(
                IllegalStateException.class,
                () -> missing.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
        assertTrue(noMemory.getMessage().contains("no memory"));

        MemoryCallOperator retrying = new MemoryCallOperator(memory);
        retrying.setParameter("max_retries", 2);
        assertThrows(IllegalArgumentException.class,
                () -> retrying.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
        verify(memory, times(3)).invoke(anyMap(), anyMap());
    }

    @Test
    @DisplayName("custom callback takes precedence")
    void testCustomCallbackPrecedence() throws Exception {
        MemoryInvoker invoker = mock(MemoryInvoker.class);
        when(invoker.invoke(anyMap())).thenReturn(Map.of("callback", "result"));
        MemoryOperation memory = mock(MemoryOperation.class);
        when(memory.invoke(anyMap(), anyMap())).thenReturn(Map.of("memory", "result"));

        MemoryCallOperator operator = new MemoryCallOperator(memory, "memory_call", invoker);
        Object result = operator.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of());

        assertEquals(Map.of("callback", "result"), result);
        verify(invoker).invoke(Map.of());
        verify(memory, never()).invoke(anyMap(), anyMap());
    }

    @Test
    @DisplayName("get state with custom values via loadState")
    void testGetStateWithCustomValues() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        operator.loadState(Map.of("enabled", false, "max_retries", 3));
        Map<String, Object> state = operator.getState();
        assertFalse((Boolean) state.get("enabled"));
        assertEquals(3, state.get("max_retries"));
    }

    @Test
    @DisplayName("load state partial only updates provided keys")
    void testLoadStatePartial() {
        MemoryOperation memory = mock(MemoryOperation.class);
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        operator.loadState(Map.of("enabled", false));
        Map<String, Object> state = operator.getState();
        assertFalse((Boolean) state.get("enabled"));
        assertEquals(0, state.get("max_retries"));
    }

    @Test
    @DisplayName("invoke with retries succeeds on first attempt")
    void testInvokeWithRetriesSuccessFirst() throws Exception {
        MemoryOperation memory = mock(MemoryOperation.class);
        when(memory.invoke(anyMap(), anyMap())).thenReturn(Map.of("ok", true));
        MemoryCallOperator operator = new MemoryCallOperator(memory);
        operator.setParameter("max_retries", 3);
        Object result = operator.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of());
        assertEquals(Map.of("ok", true), result);
        verify(memory, times(1)).invoke(anyMap(), anyMap());
    }

    @Test
    @DisplayName("invoke disabled with memory_invoke callback does not call callback")
    void testInvokeDisabledMemoryInvokeMode() throws Exception {
        MemoryInvoker invoker = mock(MemoryInvoker.class);
        MemoryCallOperator operator = new MemoryCallOperator(null, "memory_call", invoker);
        operator.setParameter("enabled", false);
        assertThrows(IllegalStateException.class,
                () -> operator.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
        verify(invoker, never()).invoke(anyMap());
    }

    @Test
    @DisplayName("stream yields chunks and clears context")
    void testStreamBasicAndCleanup() throws Exception {
        MemoryOperation streamingMemory = new MemoryOperation() {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return null;
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.<Object>of("chunk1", "chunk2").iterator();
            }
        };

        MemoryCallOperator operator = new MemoryCallOperator(streamingMemory);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();
        List<Object> chunks = new ArrayList<>();
        Iterator<Object> iterator = operator.stream(Map.of(), session, Map.of());
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertEquals(List.of("chunk1", "chunk2"), chunks);
        assertEquals(Arrays.asList("memory_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }

    @Test
    @DisplayName("stream close clears context on early termination")
    void testStreamEarlyCloseClearsContext() throws Exception {
        MemoryOperation streamingMemory = new MemoryOperation() {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return null;
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.<Object>of("chunk1", "chunk2").iterator();
            }
        };

        MemoryCallOperator operator = new MemoryCallOperator(streamingMemory);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();
        OperatorStream<Object> iterator = operator.stream(Map.of(), session, Map.of());
        iterator.next();
        iterator.close();

        assertEquals(Arrays.asList("memory_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }

    @Test
    @DisplayName("stream not implemented and unknown parameter ignored")
    void testStreamNotImplementedAndUnknownTarget() {
        MemoryCallOperator operator = new MemoryCallOperator(new MemoryOperation() {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return Map.of();
            }
        });

        assertDoesNotThrow(() -> operator.setParameter("unknown", "value"));
        assertThrows(UnsupportedOperationException.class,
                () -> operator.stream(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
    }
}
