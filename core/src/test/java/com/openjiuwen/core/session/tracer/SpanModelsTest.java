/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanModelsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Span updates fields and appends child invokes")
    void testSpanUpdateAndChildren() {
        Span span = new Span("trace-1", "invoke-1", "parent-1");
        LocalDateTime now = LocalDateTime.of(2026, 6, 6, 1, 0);
        span.update(Map.of(
                "startTime", now,
                "status", "running",
                "inputs", Map.of("key", "value")
        ));
        span.appendChildInvokeId("child-1");
        span.appendChildInvokeId("child-2");

        assertEquals(now, span.getStartTime());
        assertEquals("running", span.getStatus());
        assertEquals(Map.of("key", "value"), span.getInputs());
        assertEquals(List.of("child-1", "child-2"), span.getChildInvokesId());
    }

    @Test
    @DisplayName("TraceAgentSpan serializes Python alias names")
    void testTraceAgentSpanAliases() throws Exception {
        TraceAgentSpan span = new TraceAgentSpan("trace-1", "invoke-1", null);
        span.update(Map.of(
                "invokeType", "llm",
                "name", "Openai",
                "elapsedTime", "120ms",
                "metaData", Map.of("tool_count", 2)
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(span), Map.class);
        assertEquals("llm", payload.get("invokeType"));
        assertEquals("Openai", payload.get("name"));
        assertEquals("120ms", payload.get("elapsedTime"));
        assertTrue(payload.containsKey("metaData"));
    }

    @Test
    @DisplayName("TraceWorkflowSpan tracks stream chunks and serialized aliases")
    void testTraceWorkflowSpanAliases() throws Exception {
        TraceWorkflowSpan span = new TraceWorkflowSpan("trace-1", "invoke-1", null, "parent-node");
        span.update(Map.of(
                "workflowId", "wf-1",
                "componentId", "comp-1",
                "loopIndex", 3,
                "innerError", Map.of("message", "boom")
        ));
        span.appendStreamInputs(Map.of("chunk", "input-1"));
        span.appendStreamOutput(Map.of("chunk", "output-1"));

        assertEquals("wf-1", span.getWorkflowId());
        assertEquals("comp-1", span.getComponentId());
        assertEquals(3, span.getLoopIndex());
        assertEquals(List.of(Map.of("chunk", "input-1")), span.getStreamInputs());
        assertEquals(List.of(Map.of("chunk", "output-1")), span.getStreamOutputs());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(span), Map.class);
        assertEquals("trace-1", payload.get("executionId"));
        assertTrue(payload.containsKey("streamInputs"));
        assertTrue(payload.containsKey("streamOutputs"));
        assertTrue(payload.containsKey("innerError"));
    }

    @Test
    @DisplayName("SpanManager mirrors parent-child span bookkeeping")
    void testSpanManager() {
        SpanManager manager = new SpanManager("trace-1", "parent-node");
        TraceAgentSpan parent = manager.createAgentSpan();
        TraceAgentSpan child = manager.createAgentSpan(parent);
        TraceWorkflowSpan workflow = manager.createWorkflowSpan("node-1");

        assertEquals(parent.getInvokeId(), child.getParentInvokeId());
        assertEquals(List.of(child.getInvokeId()), parent.getChildInvokesId());
        assertEquals("parent-node", workflow.getParentNodeId());
        assertEquals("trace-1", workflow.getExecutionId());
        assertEquals(workflow.getInvokeId(), manager.getLastSpan().getInvokeId());

        manager.popSpan(workflow.getInvokeId());
        assertNull(manager.getSpan(workflow.getInvokeId()));
    }
}
