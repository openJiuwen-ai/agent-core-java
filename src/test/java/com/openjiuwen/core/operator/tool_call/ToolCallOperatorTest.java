/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.OperatorTestSupport;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Port of Python ToolCallOperator tests.
 */
class ToolCallOperatorTest {

    @Test
    @DisplayName("operator id and tunables")
    void testOperatorIdAndTunables() {
        TestTool tool = new TestTool();
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolCallOperator operator = new ToolCallOperator(tool);
        ToolCallOperator custom = new ToolCallOperator(tool, "custom_tool", null, null);
        ToolCallOperator withRegistry = new ToolCallOperator(tool, "tool_call", null, registry);

        assertEquals("tool_call", operator.getOperatorId());
        assertEquals("custom_tool", custom.getOperatorId());
        assertFalse(operator.getTunables().containsKey("tool_description"));

        Map<String, TunableSpec> tunables = withRegistry.getTunables();
        assertTrue(tunables.containsKey("tool_description"));
        assertEquals("text", tunables.get("tool_description").kind());
    }

    @Test
    @DisplayName("set parameter updates registry and ignores invalid input")
    void testSetParameter() {
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolCallOperator operator = new ToolCallOperator(new TestTool(), "tool_call", null, registry);

        operator.setParameter("tool_description", Map.of(
                "tool1", "Updated description 1",
                "tool2", "Updated description 2"));
        verify(registry).setToolDescription("tool1", "Updated description 1");
        verify(registry).setToolDescription("tool2", "Updated description 2");

        assertDoesNotThrow(() -> operator.setParameter("unknown", "value"));
        operator.setParameter("tool_description", "not a dict");
        verify(registry).setToolDescription("tool1", "Updated description 1");
        verify(registry).setToolDescription("tool2", "Updated description 2");

        ToolCallOperator noRegistry = new ToolCallOperator(new TestTool());
        assertDoesNotThrow(() -> noRegistry.setParameter("tool_description", Map.of("tool1", "desc")));
    }

    @Test
    @DisplayName("invoke direct mode and kwargs")
    void testInvokeBasicAndKwargs() throws Exception {
        TestTool tool = new TestTool();
        tool.invokeResult = Map.of("result", "success");
        ToolCallOperator operator = new ToolCallOperator(tool);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        Object result = operator.invoke(Map.of("param", "value"), session, Map.of("extra_arg", "test"));

        assertEquals(Map.of("result", "success"), result);
        assertEquals(Map.of("param", "value"), tool.lastInputs);
        assertEquals(Map.of("extra_arg", "test"), tool.lastKwargs);
        assertEquals(Arrays.asList("tool_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }

    @Test
    @DisplayName("invoke handles missing tool and router mode")
    void testInvokeMissingToolAndRouterMode() throws Exception {
        ToolCallOperator missing = new ToolCallOperator();
        IllegalStateException noTool = assertThrows(
                IllegalStateException.class,
                () -> missing.invoke(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
        assertTrue(noTool.getMessage().contains("no tool"));

        AtomicInteger executions = new AtomicInteger();
        ToolExecutor executor = (toolCall, session) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> call = (Map<String, Object>) toolCall;
            executions.incrementAndGet();
            return new ToolExecutionResult(Map.of("result", "executed " + call.get("name")), null);
        };
        ToolCallOperator router = new ToolCallOperator(null, "tool_call", executor, null);
        @SuppressWarnings("unchecked")
        List<ToolExecutionResult> results = (List<ToolExecutionResult>) router.invoke(
                Map.of("tool_calls", List.of(
                        Map.of("name", "func1", "args", Map.of()),
                        Map.of("name", "func2", "args", Map.of()))),
                new OperatorTestSupport.TrackingSession(),
                Map.of());

        assertEquals(2, results.size());
        assertEquals(2, executions.get());
        assertEquals(Map.of("result", "executed func1"), results.get(0).result());
        assertEquals(Map.of("result", "executed func2"), results.get(1).result());
    }

    @Test
    @DisplayName("stream yields chunks and clears context")
    void testStreamBasicAndCleanup() throws Exception {
        TestTool tool = new TestTool();
        tool.streamValues = List.of("chunk1", "chunk2");
        ToolCallOperator operator = new ToolCallOperator(tool);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        List<Object> chunks = new ArrayList<>();
        Iterator<Object> iterator = operator.stream(Map.of(), session, Map.of());
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertEquals(List.of("chunk1", "chunk2"), chunks);
        assertEquals(Arrays.asList("tool_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }

    @Test
    @DisplayName("stream close clears context on early termination")
    void testStreamEarlyCloseClearsContext() throws Exception {
        TestTool tool = new TestTool();
        tool.streamValues = List.of("chunk1", "chunk2");
        ToolCallOperator operator = new ToolCallOperator(tool);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        OperatorStream<Object> iterator = operator.stream(Map.of(), session, Map.of());
        iterator.next();
        iterator.close();

        assertEquals(Arrays.asList("tool_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }

    @Test
    @DisplayName("stream not implemented equivalent")
    void testStreamNotImplemented() {
        TestTool tool = new TestTool();
        tool.streamException = new UnsupportedOperationException("tool stream not implemented");
        ToolCallOperator operator = new ToolCallOperator(tool);

        assertThrows(UnsupportedOperationException.class,
                () -> operator.stream(Map.of(), new OperatorTestSupport.TrackingSession(), Map.of()));
    }

    private static final class TestTool extends Tool {

        private Object invokeResult = Map.of("result", "success");
        private List<Object> streamValues = List.of();
        private RuntimeException streamException;
        private Map<String, Object> lastInputs;
        private Map<String, Object> lastKwargs;

        private TestTool() {
            super(ToolCard.builder()
                    .id("tool-id")
                    .name("test-tool")
                    .description("test tool")
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            this.lastInputs = inputs;
            this.lastKwargs = kwargs;
            return invokeResult;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            if (streamException != null) {
                throw streamException;
            }
            return streamValues.iterator();
        }
    }
}
