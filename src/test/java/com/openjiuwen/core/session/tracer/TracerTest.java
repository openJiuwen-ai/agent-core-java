/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for tracer subsystem: {@link Tracer}, {@link SpanManager}, {@link Span},
 * {@link TraceAgentSpan}, {@link TraceWorkflowSpan}.
 * <p>
 * Mirrors Python's tracer test files, including
 * {@code tests/unit_tests/core/session/tracer/test_agent.py}.
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

        @Test
        @DisplayName("agent tracer records llm plugin workflow and custom chunks")
        void testAgentWorkflowSeqExecStreamWorkflowWithTracer() throws Exception {
            AgentSessionApi agentSession = AgentSessionApi.create(
                    "test",
                    null,
                    AgentCard.builder().id("test_agent_checkpoint").build(),
                    List.of(StreamMode.TRACE, StreamMode.CUSTOM));
            List<Object> tracerChunks = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Void> tracerCollector = CompletableFuture.runAsync(
                    () -> agentSession.streamOutput(tracerChunks::add));

            try {
                agentSession.preRun(Map.of());
                Tracer tracer = agentSession.getInner().tracerTyped();
                TraceAgentSpan agentSpan = tracer.getTracerAgentSpanManager().createAgentSpan(null);

                triggerAgentStart(tracer, agentSpan);
                triggerLlmRun(tracer, tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan));
                triggerPluginRun(tracer, tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan));

                List<Map<String, Object>> customChunks = collectWorkflowCustomChunks(
                        buildAgentTracerWorkflow(), agentSession);
                assertEquals(List.of(
                        Map.of("node_id", "a", "id", 1, "data", "1"),
                        Map.of("node_id", "a", "id", 2, "data", "2"),
                        Map.of("node_id", "b", "id", 1, "data", "1"),
                        Map.of("node_id", "b", "id", 2, "data", "2")
                ), customChunks);

                triggerAgentEnd(tracer, agentSpan);
            } finally {
                agentSession.postRun();
            }

            tracerCollector.get(5, TimeUnit.SECONDS);
            CheckpointerFactory.getCheckpointer().release(agentSession.getSessionId());

            List<Object> payloads = tracerChunks.stream()
                    .filter(TraceSchema.class::isInstance)
                    .map(TraceSchema.class::cast)
                    .map(TraceSchema::getPayload)
                    .toList();

            assertTrue(payloads.stream()
                    .filter(TraceAgentSpan.class::isInstance)
                    .map(TraceAgentSpan.class::cast)
                    .anyMatch(span -> InvokeType.CHAIN.getValue().equals(span.getInvokeType())
                            && "Agent".equals(span.getName())
                            && NodeStatus.FINISH.getValue().equals(span.getStatus())));
            assertTrue(payloads.stream()
                    .filter(TraceAgentSpan.class::isInstance)
                    .map(TraceAgentSpan.class::cast)
                    .anyMatch(span -> InvokeType.LLM.getValue().equals(span.getInvokeType())
                            && "Openai".equals(span.getName())
                            && NodeStatus.FINISH.getValue().equals(span.getStatus())));
            assertTrue(payloads.stream()
                    .filter(TraceAgentSpan.class::isInstance)
                    .map(TraceAgentSpan.class::cast)
                    .anyMatch(span -> InvokeType.PLUGIN.getValue().equals(span.getInvokeType())
                            && "RestFulAPI".equals(span.getName())
                            && NodeStatus.FINISH.getValue().equals(span.getStatus())));

            assertWorkflowTrace(payloads, "a");
            assertWorkflowTrace(payloads, "b");
        }
    }

    private static void triggerAgentStart(Tracer tracer, TraceAgentSpan span) {
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_chain_start", Map.of(
                "span", span,
                "inputs", Map.of("input", "mock chain"),
                "instance_info", Map.of("class_name", "Agent")));
    }

    private static void triggerAgentEnd(Tracer tracer, TraceAgentSpan span) {
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_chain_end", Map.of(
                "span", span,
                "outputs", Map.of("outputs", "mock chain")));
    }

    private static void triggerLlmRun(Tracer tracer, TraceAgentSpan span) {
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_llm_start", Map.of(
                "span", span,
                "inputs", Map.of("llm", "mock llm"),
                "instance_info", Map.of("class_name", "Openai")));
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_llm_end", Map.of(
                "span", span,
                "outputs", Map.of("outputs", "mock llm")));
    }

    private static void triggerPluginRun(Tracer tracer, TraceAgentSpan span) {
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_plugin_start", Map.of(
                "span", span,
                "inputs", Map.of("llm", "mock tool"),
                "instance_info", Map.of("class_name", "RestFulAPI")));
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_plugin_end", Map.of(
                "span", span,
                "outputs", Map.of("outputs", "mock tool")));
    }

    private static Workflow buildAgentTracerWorkflow() {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .id("agent-tracer-workflow")
                .name("Agent Tracer Workflow")
                .version("1")
                .build());
        flow.setStartComp("start", new Start(), Map.of(
                "a", "${a}",
                "b", "${b}",
                "c", 1,
                "d", List.of(1, 2, 3)), null);
        flow.addWorkflowComp("a", new StreamNodeWithTracer(List.of(
                Map.of("node_id", "a", "id", 1, "data", "1"),
                Map.of("node_id", "a", "id", 2, "data", "2"))),
                Map.of("aa", "${start.a}", "ac", "${start.c}"));
        flow.addWorkflowComp("b", new StreamNodeWithTracer(List.of(
                Map.of("node_id", "b", "id", 1, "data", "1"),
                Map.of("node_id", "b", "id", 2, "data", "2"))),
                Map.of("ba", "${a.aa}", "bc", "${a.ac}"));
        flow.setEndComp("end", new PassthroughNode(), Map.of("result", "${b.ba}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("a", "b");
        flow.addConnection("b", "end");
        return flow;
    }

    private static List<Map<String, Object>> collectWorkflowCustomChunks(
            Workflow workflow, AgentSessionApi agentSession) {
        Iterator<WorkflowChunk> iterator = workflow.stream(
                Map.of("a", 1, "b", "haha"),
                agentSession.createWorkflowSession(),
                null,
                List.of(StreamMode.CUSTOM));
        List<Map<String, Object>> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunk -> {
            if (chunk instanceof CustomSchema customSchema) {
                chunks.add(customSchema.getProperties());
            }
        });
        return chunks;
    }

    private static void assertWorkflowTrace(List<Object> payloads, String componentId) {
        assertTrue(payloads.stream()
                .filter(TraceWorkflowSpan.class::isInstance)
                .map(TraceWorkflowSpan.class::cast)
                .filter(span -> componentId.equals(span.getComponentId()))
                .anyMatch(span -> span.getOnInvokeData() != null
                        && span.getOnInvokeData().stream().anyMatch(item ->
                                String.valueOf(item.get("on_invoke_data")).contains("mock with"))));
        assertTrue(payloads.stream()
                .filter(TraceWorkflowSpan.class::isInstance)
                .map(TraceWorkflowSpan.class::cast)
                .anyMatch(span -> componentId.equals(span.getInvokeId())
                        && NodeStatus.FINISH.getValue().equals(span.getStatus())));
    }

    private static class StreamNodeWithTracer extends WorkflowComponent {
        private final List<Map<String, Object>> chunks;

        StreamNodeWithTracer(List<Map<String, Object>> chunks) {
            this.chunks = chunks;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            try {
                session.trace(Map.of("on_invoke_data", "mock with" + inputs));
                for (Map<String, Object> chunk : chunks) {
                    session.writeCustomStream(chunk);
                }
                return inputs;
            } catch (RuntimeException e) {
                session.traceError(e);
                throw e;
            }
        }
    }

    private static class PassthroughNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }
}
