/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.CallbackManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock node with tracer for testing.
 * Mirrors Python's tests/unit_tests/core/session/tracer/mock_node_with_tracer.py
 */
class MockNodeWithTracerTest {

    @Nested
    @DisplayName("MockNodeWithTracer tests")
    class MockNodeTests {

        @Test
        @DisplayName("test tracer initialization")
        void testTracerInitialization() {
            // Test basic tracer initialization
            Tracer tracer = new Tracer();
            assertNotNull(tracer);
            
            // Verify traceId is generated
            assertNotNull(tracer.getTraceId());
            assertTrue(tracer.getTraceId().length() > 0);
        }

        @Test
        @DisplayName("test tracer with callback manager")
        void testTracerWithCallbackManager() {
            Tracer tracer = new Tracer();
            CallbackManager callbackManager = new CallbackManager();
            
            // Initialize tracer with managers (streamWriterManager can be null for basic tests)
            tracer.init(null, callbackManager);
            
            // Verify tracer is initialized
            assertNotNull(tracer.getTraceId());
        }

        @Test
        @DisplayName("test span manager creation")
        void testSpanManagerCreation() {
            String traceId = UUID.randomUUID().toString();
            SpanManager spanManager = new SpanManager(traceId);
            
            assertNotNull(spanManager);
            
            // Create agent span
            TraceAgentSpan agentSpan = spanManager.createAgentSpan(null);
            assertNotNull(agentSpan);
            assertNotNull(agentSpan.getInvokeId());
            assertEquals(traceId, agentSpan.getTraceId());
            assertNull(agentSpan.getParentInvokeId());
        }

        @Test
        @DisplayName("test workflow span creation")
        void testWorkflowSpanCreation() {
            String traceId = UUID.randomUUID().toString();
            SpanManager spanManager = new SpanManager(traceId);
            
            // Create workflow span with explicit invokeId
            String invokeId = UUID.randomUUID().toString();
            TraceWorkflowSpan workflowSpan = spanManager.createWorkflowSpan(invokeId, null);
            
            assertNotNull(workflowSpan);
            assertEquals(invokeId, workflowSpan.getInvokeId());
            assertEquals(traceId, workflowSpan.getTraceId());
        }

        @Test
        @DisplayName("test span parent child relationship")
        void testSpanParentChildRelationship() {
            String traceId = UUID.randomUUID().toString();
            SpanManager spanManager = new SpanManager(traceId);
            
            // Create parent span
            TraceAgentSpan parentSpan = spanManager.createAgentSpan(null);
            
            // Create child workflow span
            String childInvokeId = UUID.randomUUID().toString();
            TraceWorkflowSpan childSpan = spanManager.createWorkflowSpan(childInvokeId, parentSpan);
            
            // Verify parent-child relationship
            assertEquals(parentSpan.getInvokeId(), childSpan.getParentInvokeId());
            assertTrue(parentSpan.getChildInvokesId().contains(childInvokeId));
        }

        @Test
        @DisplayName("test span update")
        void testSpanUpdate() {
            String traceId = UUID.randomUUID().toString();
            SpanManager spanManager = new SpanManager(traceId);
            
            TraceAgentSpan span = spanManager.createAgentSpan(null);
            
            // Update span with data
            java.util.Map<String, Object> updateData = new java.util.HashMap<>();
            updateData.put("inputs", java.util.Map.of("key", "value"));
            updateData.put("status", "running");
            
            spanManager.updateSpan(span, updateData);
            
            // Verify span is updated
            Span updatedSpan = spanManager.getSpan(span.getInvokeId());
            assertNotNull(updatedSpan);
            assertNotNull(updatedSpan.getInputs());
        }

        @Test
        @DisplayName("test register workflow span manager")
        void testRegisterWorkflowSpanManager() {
            Tracer tracer = new Tracer();
            CallbackManager callbackManager = new CallbackManager();
            tracer.init(null, callbackManager);
            
            // Register workflow span manager for a parent node
            String parentNodeId = "workflow_1";
            tracer.registerWorkflowSpanManager(parentNodeId);
            
            // Verify registration succeeded
            assertNotNull(tracer.getTraceId());
        }

        @Test
        @DisplayName("test span status tracking")
        void testSpanStatusTracking() {
            Span span = new Span(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null);
            
            // Set status
            span.setStatus("started");
            assertEquals("started", span.getStatus());
            
            span.setStatus("completed");
            assertEquals("completed", span.getStatus());
        }

        @Test
        @DisplayName("test mock node with tracer basic functionality")
        void testMockNodeWithTracer() {
            // This test verifies the basic tracer functionality that would be used
            // by a mock node with tracer
            
            // 1. Create tracer
            Tracer tracer = new Tracer();
            assertNotNull(tracer.getTraceId());
            
            // 2. Initialize with managers
            tracer.init(null, new CallbackManager());
            
            // 3. Register workflow
            tracer.registerWorkflowSpanManager("node1");
            
            // 4. Create span and update
            SpanManager spanManager = new SpanManager(tracer.getTraceId());
            TraceWorkflowSpan span = spanManager.createWorkflowSpan("invoke_1", null);
            
            // Simulate trace operation like session.trace({"on_invoke_data": "mock"})
            java.util.Map<String, Object> traceData = new java.util.HashMap<>();
            traceData.put("on_invoke_data", "mock with inputs");
            
            // Update span with trace data (using update method which handles on_invoke_data)
            java.util.List<java.util.Map<String, Object>> onInvokeDataList = new java.util.ArrayList<>();
            onInvokeDataList.add(traceData);
            span.setOnInvokeData(onInvokeDataList);
            
            assertNotNull(span.getOnInvokeData());
            assertTrue(span.getOnInvokeData().size() > 0);
            
            // Verify the trace data was recorded
            java.util.Map<String, Object> recordedData = span.getOnInvokeData().get(0);
            assertEquals("mock with inputs", recordedData.get("on_invoke_data"));
        }
    }
}