/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkflowTracer.
 * Mirrors Python's tests/unit_tests/core/session/tracer/test_workflow_tracer.py
 */
class TestWorkflowTracer {

    @Nested
    @DisplayName("WorkflowTracer tests")
    class TracerTests {

        @Test
        @DisplayName("test tracer creation")
        void testTracerCreation() {
            Tracer tracer = new Tracer();
            assertNotNull(tracer);
            assertNotNull(tracer.getTraceId());
        }

        @Test
        @DisplayName("test workflow span creation and retrieval")
        void testWorkflowSpanCreation() {
            String traceId = UUID.randomUUID().toString();
            SpanManager spanManager = new SpanManager(traceId, "workflow_node");
            
            String invokeId = UUID.randomUUID().toString();
            TraceWorkflowSpan span = spanManager.createWorkflowSpan(invokeId, null);
            
            assertNotNull(span);
            assertEquals(invokeId, span.getInvokeId());
            assertEquals(traceId, span.getTraceId());
        }

        @Test
        @DisplayName("test span data recording")
        void testSpanDataRecording() {
            String traceId = UUID.randomUUID().toString();
            SpanManager spanManager = new SpanManager(traceId);
            
            TraceWorkflowSpan span = spanManager.createWorkflowSpan("test_invoke", null);
            
            // Record inputs
            java.util.Map<String, Object> inputs = new java.util.HashMap<>();
            inputs.put("query", "test query");
            span.setInputs(inputs);
            
            // Record outputs
            java.util.Map<String, Object> outputs = new java.util.HashMap<>();
            outputs.put("result", "test result");
            span.setOutputs(outputs);
            
            assertEquals(inputs, span.getInputs());
            assertEquals(outputs, span.getOutputs());
        }

        @Test
        @DisplayName("test stream data appending")
        void testStreamDataAppending() {
            TraceWorkflowSpan span = new TraceWorkflowSpan(
                UUID.randomUUID().toString(), 
                UUID.randomUUID().toString(), 
                null, 
                "parent_node"
            );
            
            // Append stream outputs
            java.util.Map<String, Object> chunk1 = new java.util.HashMap<>();
            chunk1.put("data", "chunk1");
            span.appendStreamOutput(chunk1);
            
            java.util.Map<String, Object> chunk2 = new java.util.HashMap<>();
            chunk2.put("data", "chunk2");
            span.appendStreamOutput(chunk2);
            
            assertNotNull(span.getStreamOutputs());
            assertEquals(2, span.getStreamOutputs().size());
        }

        @Test
        @DisplayName("test workflow tracer basic functionality")
        void testWorkflowTracer() {
            // Basic tracer functionality test
            Tracer tracer = new Tracer();
            
            // Verify tracer ID is generated
            String traceId = tracer.getTraceId();
            assertNotNull(traceId);
            assertTrue(traceId.length() > 0);
            
            // Verify span manager is initialized
            SpanManager agentSpanManager = tracer.getTracerAgentSpanManager();
            assertNotNull(agentSpanManager);
            assertEquals(traceId, agentSpanManager.getTraceId());
        }
    }
}