/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Span, TraceAgentSpan, TraceWorkflowSpan, and SpanManager.
 * 
 * <p>Converted from Python: test_span.py</p>
 */
class SpanTest {
    
    @Nested
    @DisplayName("Span Tests")
    class SpanTests {
        
        @Test
        @DisplayName("construction with required fields")
        void testConstructionWithRequiredFields() {
            Span span = new Span("trace123");
            assertEquals("trace123", span.getTraceId());
            assertNull(span.getStartTime());
            assertNull(span.getEndTime());
            assertNull(span.getInputs());
            assertNull(span.getOutputs());
            assertNull(span.getError());
            assertNull(span.getInvokeId());
            assertNull(span.getParentInvokeId());
            assertNull(span.getChildInvokesId());
            assertNull(span.getStatus());
            assertNull(span.getOnInvokeData());
        }
        
        @Test
        @DisplayName("construction with all fields")
        void testConstructionWithAllFields() {
            Instant now = Instant.now();
            Span span = new Span("trace123");
            span.setStartTime(now);
            span.setEndTime(now);
            span.setInputs(Map.of("key", "value"));
            span.setOutputs(Map.of("result", "output"));
            span.setError(Map.of("message", "error"));
            span.setInvokeId("invoke123");
            span.setParentInvokeId("parent123");
            span.setChildInvokesId(new ArrayList<>(List.of("child1", "child2")));
            span.setStatus("success");
            
            assertEquals("trace123", span.getTraceId());
            assertEquals(now, span.getStartTime());
            assertEquals("invoke123", span.getInvokeId());
            assertEquals(List.of("child1", "child2"), span.getChildInvokesId());
        }
        
        @Test
        @DisplayName("update updates existing attributes")
        void testUpdateUpdatesExistingAttributes() {
            Span span = new Span("trace123");
            span.update(Map.of(
                "status", "running",
                "inputs", Map.of("query", "test")
            ));
            assertEquals("running", span.getStatus());
            assertEquals(Map.of("query", "test"), span.getInputs());
        }
        
        @Test
        @DisplayName("update ignores unknown attributes")
        void testUpdateIgnoresUnknownAttributes() {
            Span span = new Span("trace123");
            span.update(Map.of(
                "unknownField", "value",
                "status", "success"
            ));
            assertEquals("success", span.getStatus());
        }
        
        @Test
        @DisplayName("append child invoke id to empty list")
        void testAppendChildInvokeIdToEmptyList() {
            Span span = new Span("trace123");
            span.appendChildInvokeId("child1");
            assertEquals(List.of("child1"), span.getChildInvokesId());
        }
        
        @Test
        @DisplayName("append child invoke id to existing list")
        void testAppendChildInvokeIdToExistingList() {
            Span span = new Span("trace123");
            span.setChildInvokesId(new ArrayList<>(List.of("child1")));
            span.appendChildInvokeId("child2");
            assertEquals(List.of("child1", "child2"), span.getChildInvokesId());
        }
    }
    
    @Nested
    @DisplayName("TraceAgentSpan Tests")
    class TraceAgentSpanTests {
        
        @Test
        @DisplayName("construction with agent fields")
        void testConstructionWithAgentFields() {
            TraceAgentSpan span = new TraceAgentSpan("trace123");
            span.setInvokeType("agent");
            span.setName("test_agent");
            span.setElapsedTime("100ms");
            span.setMetaData(Map.of("tokens", 100));
            
            assertEquals("agent", span.getInvokeType());
            assertEquals("test_agent", span.getName());
            assertEquals("100ms", span.getElapsedTime());
            assertEquals(Map.of("tokens", 100), span.getMetaData());
        }
        
        @Test
        @DisplayName("inherits span behavior")
        void testInheritsSpanBehavior() {
            TraceAgentSpan span = new TraceAgentSpan("trace123");
            span.appendChildInvokeId("child1");
            assertEquals(List.of("child1"), span.getChildInvokesId());
        }
        
        @Test
        @DisplayName("update agent specific fields")
        void testUpdateAgentSpecificFields() {
            TraceAgentSpan span = new TraceAgentSpan("trace123");
            span.update(Map.of(
                "invokeType", "llm",
                "name", "gpt-4"
            ));
            assertEquals("llm", span.getInvokeType());
            assertEquals("gpt-4", span.getName());
        }
    }
    
    @Nested
    @DisplayName("TraceWorkflowSpan Tests")
    class TraceWorkflowSpanTests {
        
        @Test
        @DisplayName("construction with workflow fields")
        void testConstructionWithWorkflowFields() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace123");
            span.setExecutionId("exec123");
            span.setWorkflowId("wf123");
            span.setWorkflowName("test_workflow");
            span.setComponentId("comp123");
            span.setComponentName("start");
            span.setComponentType("StartComponent");
            
            assertEquals("exec123", span.getExecutionId());
            assertEquals("wf123", span.getWorkflowId());
            assertEquals("test_workflow", span.getWorkflowName());
            assertEquals("comp123", span.getComponentId());
            assertEquals("start", span.getComponentName());
            assertEquals("StartComponent", span.getComponentType());
        }
        
        @Test
        @DisplayName("loop fields")
        void testLoopFields() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace123");
            span.setLoopNodeId("loop1");
            span.setLoopIndex(5);
            
            assertEquals("loop1", span.getLoopNodeId());
            assertEquals(5, span.getLoopIndex());
        }
        
        @Test
        @DisplayName("append stream output to empty")
        void testAppendStreamOutputToEmpty() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace123");
            span.appendStreamOutput("chunk1");
            assertEquals(List.of("chunk1"), span.getStreamOutputs());
        }
        
        @Test
        @DisplayName("append stream output to existing")
        void testAppendStreamOutputToExisting() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace123");
            span.setStreamOutputs(new ArrayList<>(List.of("chunk1")));
            span.appendStreamOutput("chunk2");
            assertEquals(List.of("chunk1", "chunk2"), span.getStreamOutputs());
        }
        
        @Test
        @DisplayName("append stream inputs to empty")
        void testAppendStreamInputsToEmpty() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace123");
            span.appendStreamInputs("input1");
            assertEquals(List.of("input1"), span.getStreamInputs());
        }
        
        @Test
        @DisplayName("append stream inputs to existing")
        void testAppendStreamInputsToExisting() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace123");
            span.setStreamInputs(new ArrayList<>(List.of("input1")));
            span.appendStreamInputs("input2");
            assertEquals(List.of("input1", "input2"), span.getStreamInputs());
        }
    }
    
    @Nested
    @DisplayName("SpanManager Tests")
    class SpanManagerTests {
        
        private SpanManager spanManager;
        
        @BeforeEach
        void setUp() {
            spanManager = new SpanManager("trace123", "parent1");
        }
        
        @Test
        @DisplayName("construction")
        void testConstruction() {
            assertEquals("trace123", spanManager.getTraceId());
            assertEquals("parent1", spanManager.getParentNodeId());
        }
        
        @Test
        @DisplayName("get span returns null for nonexistent")
        void testGetSpanReturnsNullForNonexistent() {
            Span result = spanManager.getSpan("nonexistent");
            assertNull(result);
        }
        
        @Test
        @DisplayName("get span returns span")
        void testGetSpanReturnsSpan() {
            TraceAgentSpan span = spanManager.createAgentSpan();
            Span result = spanManager.getSpan(span.getInvokeId());
            assertSame(span, result);
        }
        
        @Test
        @DisplayName("pop span removes span")
        void testPopSpanRemovesSpan() {
            TraceAgentSpan span = spanManager.createAgentSpan();
            String invokeId = span.getInvokeId();
            spanManager.popSpan(invokeId);
            assertNull(spanManager.getSpan(invokeId));
        }
        
        @Test
        @DisplayName("pop span nonexistent no error")
        void testPopSpanNonexistentNoError() {
            spanManager.popSpan("nonexistent"); // Should not raise
        }
        
        @Test
        @DisplayName("create agent span generates uuid")
        void testCreateAgentSpanGeneratesUuid() {
            TraceAgentSpan span = spanManager.createAgentSpan();
            assertNotNull(span.getInvokeId());
            assertTrue(span.getInvokeId().length() > 0);
            assertEquals("trace123", span.getTraceId());
            assertNull(span.getParentInvokeId());
        }
        
        @Test
        @DisplayName("create agent span with parent")
        void testCreateAgentSpanWithParent() {
            TraceAgentSpan parentSpan = spanManager.createAgentSpan();
            TraceAgentSpan childSpan = spanManager.createAgentSpan(parentSpan);
            assertEquals(parentSpan.getInvokeId(), childSpan.getParentInvokeId());
            assertEquals(List.of(childSpan.getInvokeId()), parentSpan.getChildInvokesId());
        }
        
        @Test
        @DisplayName("create workflow span")
        void testCreateWorkflowSpan() {
            TraceWorkflowSpan span = spanManager.createWorkflowSpan("invoke123");
            assertEquals("invoke123", span.getInvokeId());
            assertEquals("trace123", span.getTraceId());
            assertEquals("parent1", span.getParentNodeId());
            assertEquals("trace123", span.getExecutionId());
        }
        
        @Test
        @DisplayName("create workflow span with parent")
        void testCreateWorkflowSpanWithParent() {
            TraceWorkflowSpan parentSpan = spanManager.createWorkflowSpan("parent_invoke");
            TraceWorkflowSpan childSpan = spanManager.createWorkflowSpan("child_invoke", parentSpan);
            assertEquals(parentSpan.getInvokeId(), childSpan.getParentInvokeId());
            assertEquals(List.of(childSpan.getInvokeId()), parentSpan.getChildInvokesId());
        }
        
        @Test
        @DisplayName("update span")
        void testUpdateSpan() {
            TraceAgentSpan span = spanManager.createAgentSpan();
            spanManager.updateSpan(span, Map.of("status", "running", "inputs", Map.of("query", "test")));
            Span retrieved = spanManager.getSpan(span.getInvokeId());
            assertEquals("running", retrieved.getStatus());
            assertEquals(Map.of("query", "test"), retrieved.getInputs());
        }
        
        @Test
        @DisplayName("last span returns null when empty")
        void testLastSpanReturnsNullWhenEmpty() {
            SpanManager emptyManager = new SpanManager("trace123");
            assertNull(emptyManager.getLastSpan());
        }
        
        @Test
        @DisplayName("last span returns last created")
        void testLastSpanReturnsLastCreated() {
            spanManager.createAgentSpan();
            spanManager.createAgentSpan();
            TraceAgentSpan span3 = spanManager.createAgentSpan();
            assertSame(span3, spanManager.getLastSpan());
        }
        
        @Test
        @DisplayName("last span after pop")
        void testLastSpanAfterPop() {
            TraceAgentSpan span1 = spanManager.createAgentSpan();
            TraceAgentSpan span2 = spanManager.createAgentSpan();
            spanManager.popSpan(span2.getInvokeId());
            assertSame(span1, spanManager.getLastSpan());
        }
        
        @Test
        @DisplayName("multiple spans ordering")
        void testMultipleSpansOrdering() {
            List<TraceAgentSpan> spans = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                spans.add(spanManager.createAgentSpan());
            }
            // Last span should be the 5th one
            assertSame(spans.get(4), spanManager.getLastSpan());
        }
    }
}

