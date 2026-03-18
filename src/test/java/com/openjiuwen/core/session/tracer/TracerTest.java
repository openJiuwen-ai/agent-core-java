/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for tracer subsystem: {@link Tracer}, {@link SpanManager}, {@link Span},
 * {@link TraceAgentSpan}, {@link TraceWorkflowSpan}.
 * <p>
 * Ported from Python's tracer test files.
 */
class TracerTest {

    // ---------- Span tests ----------

    @Nested
    @DisplayName("Span")
    class SpanTests {

        @Test
        @DisplayName("span basic construction")
        void testSpanConstruction() {
            Span span = new Span("trace-1", "invoke-1", "parent-1");
            assertEquals("trace-1", span.getTraceId());
            assertEquals("invoke-1", span.getInvokeId());
            assertEquals("parent-1", span.getParentInvokeId());
        }

        @Test
        @DisplayName("span update with map")
        void testSpanUpdate() {
            Span span = new Span("trace-1", "invoke-1", null);
            LocalDateTime now = LocalDateTime.now();
            span.update(Map.of(
                    "startTime", now,
                    "status", "running",
                    "inputs", Map.of("key", "value")
            ));
            assertEquals(now, span.getStartTime());
            assertEquals("running", span.getStatus());
            assertEquals(Map.of("key", "value"), span.getInputs());
        }

        @Test
        @DisplayName("span appendChildInvokeId")
        void testSpanAppendChild() {
            Span span = new Span("trace-1", "invoke-1", null);
            assertNull(span.getChildInvokesId());
            span.appendChildInvokeId("child-1");
            span.appendChildInvokeId("child-2");
            assertEquals(List.of("child-1", "child-2"), span.getChildInvokesId());
        }

        @Test
        @DisplayName("span setField with outputs")
        void testSpanSetFieldOutputs() {
            Span span = new Span();
            span.update(Map.of("outputs", "some-output"));
            assertEquals("some-output", span.getOutputs());
        }

        @Test
        @DisplayName("span setField with error")
        void testSpanSetFieldError() {
            Span span = new Span();
            Map<String, Object> error = Map.of("error_code", 500, "message", "test error");
            span.update(Map.of("error", error));
            assertEquals(error, span.getError());
        }
    }

    // ---------- TraceAgentSpan tests ----------

    @Nested
    @DisplayName("TraceAgentSpan")
    class AgentSpanTests {

        @Test
        @DisplayName("agent span construction with parent")
        void testAgentSpanConstruction() {
            TraceAgentSpan span = new TraceAgentSpan("trace-1", "invoke-1", "parent-1");
            assertEquals("trace-1", span.getTraceId());
            assertEquals("invoke-1", span.getInvokeId());
            assertEquals("parent-1", span.getParentInvokeId());
        }

        @Test
        @DisplayName("agent span setField - invokeType")
        void testAgentSpanInvokeType() {
            TraceAgentSpan span = new TraceAgentSpan();
            span.update(Map.of("invokeType", "LLM"));
            assertEquals("LLM", span.getInvokeType());
        }

        @Test
        @DisplayName("agent span setField - name")
        void testAgentSpanName() {
            TraceAgentSpan span = new TraceAgentSpan();
            span.update(Map.of("name", "TestAgent"));
            assertEquals("TestAgent", span.getName());
        }

        @Test
        @DisplayName("agent span setField - elapsedTime")
        void testAgentSpanElapsedTime() {
            TraceAgentSpan span = new TraceAgentSpan();
            span.update(Map.of("elapsedTime", "120ms"));
            assertEquals("120ms", span.getElapsedTime());
        }

        @Test
        @DisplayName("agent span setField - metaData")
        void testAgentSpanMetaData() {
            TraceAgentSpan span = new TraceAgentSpan();
            Map<String, Object> meta = Map.of("class_name", "MockAgent");
            span.update(Map.of("metaData", meta));
            assertEquals(meta, span.getMetaData());
        }
    }

    // ---------- TraceWorkflowSpan tests ----------

    @Nested
    @DisplayName("TraceWorkflowSpan")
    class WorkflowSpanTests {

        @Test
        @DisplayName("workflow span construction")
        void testWorkflowSpanConstruction() {
            TraceWorkflowSpan span = new TraceWorkflowSpan("trace-1", "invoke-1", "parent-1", "parentNode");
            assertEquals("trace-1", span.getTraceId());
            assertEquals("invoke-1", span.getInvokeId());
            assertEquals("parent-1", span.getParentInvokeId());
            assertEquals("parentNode", span.getParentNodeId());
            assertEquals("trace-1", span.getExecutionId());
        }

        @Test
        @DisplayName("workflow span setField - componentId")
        void testWorkflowSpanComponentId() {
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            span.update(Map.of("componentId", "comp-1"));
            assertEquals("comp-1", span.getComponentId());
        }

        @Test
        @DisplayName("workflow span setField - workflowId, version, name")
        void testWorkflowSpanWorkflowInfo() {
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            span.update(Map.of(
                    "workflowId", "wf-1",
                    "workflowVersion", "1.0",
                    "workflowName", "test workflow"
            ));
            assertEquals("wf-1", span.getWorkflowId());
            assertEquals("1.0", span.getWorkflowVersion());
            assertEquals("test workflow", span.getWorkflowName());
        }

        @Test
        @DisplayName("workflow span setField - componentType")
        void testWorkflowSpanComponentType() {
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            span.update(Map.of("componentType", "LLMNode"));
            assertEquals("LLMNode", span.getComponentType());
        }

        @Test
        @DisplayName("workflow span setField - loopNodeId and loopIndex")
        void testWorkflowSpanLoopInfo() {
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            span.update(Map.of("loopNodeId", "loop-1", "loopIndex", 3));
            assertEquals("loop-1", span.getLoopNodeId());
            assertEquals(3, span.getLoopIndex());
        }

        @Test
        @DisplayName("workflow span appendStreamOutput")
        void testWorkflowSpanAppendStreamOutput() {
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            assertNull(span.getStreamOutputs());
            span.appendStreamOutput(Map.of("data", "chunk1"));
            span.appendStreamOutput(Map.of("data", "chunk2"));
            assertEquals(2, span.getStreamOutputs().size());
        }

        @Test
        @DisplayName("workflow span appendStreamInput")
        void testWorkflowSpanAppendStreamInput() {
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            assertNull(span.getStreamInputs());
            span.appendStreamInput(Map.of("data", "input1"));
            assertEquals(1, span.getStreamInputs().size());
        }
    }

    // ---------- SpanManager tests ----------

    @Nested
    @DisplayName("SpanManager")
    class SpanManagerTests {

        private SpanManager manager;

        @BeforeEach
        void setUp() {
            manager = new SpanManager("trace-1");
        }

        @Test
        @DisplayName("create agent span without parent")
        void testCreateAgentSpanNoParent() {
            TraceAgentSpan span = manager.createAgentSpan(null);
            assertNotNull(span);
            assertNotNull(span.getInvokeId());
            assertNull(span.getParentInvokeId());
            assertEquals("trace-1", span.getTraceId());
        }

        @Test
        @DisplayName("create agent span with parent")
        void testCreateAgentSpanWithParent() {
            TraceAgentSpan parent = manager.createAgentSpan(null);
            TraceAgentSpan child = manager.createAgentSpan(parent);
            assertNotNull(child);
            assertEquals(parent.getInvokeId(), child.getParentInvokeId());
        }

        @Test
        @DisplayName("create workflow span")
        void testCreateWorkflowSpan() {
            TraceWorkflowSpan span = manager.createWorkflowSpan("node-1", null);
            assertNotNull(span);
            assertEquals("node-1", span.getInvokeId());
            assertNull(span.getParentInvokeId());
        }

        @Test
        @DisplayName("getSpan returns existing span")
        void testGetSpan() {
            TraceWorkflowSpan span = manager.createWorkflowSpan("node-1", null);
            Span retrieved = manager.getSpan("node-1");
            assertNotNull(retrieved);
            assertEquals("node-1", retrieved.getInvokeId());
        }

        @Test
        @DisplayName("getSpan returns null for non-existent span")
        void testGetSpanNonExistent() {
            assertNull(manager.getSpan("non-existent"));
        }

        @Test
        @DisplayName("popSpan removes span")
        void testPopSpan() {
            manager.createWorkflowSpan("node-1", null);
            assertNotNull(manager.getSpan("node-1"));
            manager.popSpan("node-1");
            assertNull(manager.getSpan("node-1"));
        }

        @Test
        @DisplayName("getLastSpan returns the last added span")
        void testGetLastSpan() {
            manager.createWorkflowSpan("node-1", null);
            manager.createWorkflowSpan("node-2", null);
            Span last = manager.getLastSpan();
            assertNotNull(last);
            assertEquals("node-2", last.getInvokeId());
        }

        @Test
        @DisplayName("getLastSpan returns null when empty")
        void testGetLastSpanEmpty() {
            assertNull(manager.getLastSpan());
        }

        @Test
        @DisplayName("updateSpan updates span data and refreshes record")
        void testUpdateSpan() {
            TraceWorkflowSpan span = manager.createWorkflowSpan("node-1", null);
            manager.updateSpan(span, Map.of("status", "running"));
            Span retrieved = manager.getSpan("node-1");
            assertEquals("running", retrieved.getStatus());
        }

        @Test
        @DisplayName("span manager with parentNodeId")
        void testSpanManagerWithParentNodeId() {
            SpanManager parentManager = new SpanManager("trace-1", "parent-node");
            TraceWorkflowSpan span = parentManager.createWorkflowSpan("child-1", null);
            assertEquals("parent-node", span.getParentNodeId());
        }
    }

    // ---------- Tracer tests ----------

    @Nested
    @DisplayName("Tracer")
    class TracerTests {

        @Test
        @DisplayName("tracer initialization")
        void testTracerInit() {
            Tracer tracer = new Tracer();
            assertNotNull(tracer.getTraceId());
            assertNotNull(tracer.getTracerAgentSpanManager());
        }

        @Test
        @DisplayName("tracer agent span manager creates agent span")
        void testTracerAgentSpan() {
            Tracer tracer = new Tracer();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(null);
            assertNotNull(span);
            assertNotNull(span.getInvokeId());
        }

        @Test
        @DisplayName("tracer agent span with parent-child relationship")
        void testTracerAgentSpanParentChild() {
            Tracer tracer = new Tracer();
            TraceAgentSpan parent = tracer.getTracerAgentSpanManager().createAgentSpan(null);
            TraceAgentSpan child = tracer.getTracerAgentSpanManager().createAgentSpan(parent);
            assertEquals(parent.getInvokeId(), child.getParentInvokeId());
        }

        @Test
        @DisplayName("register workflow span manager")
        void testRegisterWorkflowSpanManager() {
            Tracer tracer = new Tracer();
            com.openjiuwen.core.session.stream.StreamEmitter emitter =
                    new com.openjiuwen.core.session.stream.StreamEmitter();
            com.openjiuwen.core.session.stream.StreamWriterManager swm =
                    new com.openjiuwen.core.session.stream.StreamWriterManager(emitter);
            com.openjiuwen.core.session.callback.CallbackManager cbm =
                    new com.openjiuwen.core.session.callback.CallbackManager();
            tracer.init(swm, cbm);

            tracer.registerWorkflowSpanManager("parent-node-1");
            assertTrue(tracer.getTracerWorkflowSpanManagerDict().containsKey("parent-node-1"));
        }

        @Test
        @DisplayName("tracer getWorkflowSpan returns null for non-existent")
        void testGetWorkflowSpanNonExistent() {
            Tracer tracer = new Tracer();
            assertNull(tracer.getWorkflowSpan("non-existent", ""));
        }
    }
}
