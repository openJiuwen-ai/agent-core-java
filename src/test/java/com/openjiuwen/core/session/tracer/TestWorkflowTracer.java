/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for workflow tracing.
 * Mirrors Python's {@code tests/unit_tests/core/session/tracer/test_workflow_tracer.py}.
 */
class TestWorkflowTracer {

    @Test
    @DisplayName("test_any_type_trace")
    void testAnyTypeTrace() {
        List<Object> allTypeInputs = List.of(
                List.of(1, 2, 3),
                "abc",
                Map.of("a", 1),
                1,
                0.4,
                NullValue.INSTANCE);

        for (Object rawInput : allTypeInputs) {
            Object input = rawInput == NullValue.INSTANCE ? null : rawInput;
            WorkflowTraceHarness trace = new WorkflowTraceHarness();
            Map<String, Object> streamPayload = mapWithNulls("output", mapWithNulls("output", input));
            Map<String, Object> endFrame = mapWithNulls(
                    "type", "end node stream",
                    "index", 0,
                    "payload", streamPayload);

            trace.handler.onCallStart("end", componentMetadata("test", "end"),
                    mapWithNulls("data", input), true, List.of("node"));
            trace.handler.onPostStream("end", endFrame);
            trace.handler.onCallDone("end", null);

            TraceWorkflowSpan end = lastSpan(trace.drain(), "end");
            assertEquals(List.of(endFrame), end.getStreamOutputs());
        }
    }

    @Test
    @DisplayName("test_stream_workflow_with_trace")
    void testStreamWorkflowWithTrace() {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        List<Integer> inputs = List.of(1, 2, 3);
        List<Map<String, Object>> producerChunks = List.of(
                Map.of("output", 1),
                Map.of("output", 2),
                Map.of("output", 3));
        List<Map<String, Object>> endFrames = List.of(
                Map.of("type", "end node stream", "index", 0, "payload", Map.of("output", Map.of("output", 1))),
                Map.of("type", "end node stream", "index", 1, "payload", Map.of("output", Map.of("output", 2))),
                Map.of("type", "end node stream", "index", 2, "payload", Map.of("output", Map.of("output", 3))));

        trace.handler.onCallStart("test", workflowMetadata("test", "1.0", "test workflow"),
                Map.of("inputs", inputs), true, null);
        trace.handler.onCallStart("start", componentMetadata("test", "start"), null, true, null);
        trace.handler.onCallDone("start", null);
        trace.handler.onCallStart("producer", componentMetadata("test", "producer"),
                Map.of("array", inputs), true, List.of("start"));
        producerChunks.forEach(chunk -> trace.handler.onPostStream("producer", chunk));
        trace.handler.onCallDone("producer", null);
        trace.handler.onCallStart("end", componentMetadata("test", "end"), null, true, List.of("producer"));
        producerChunks.forEach(chunk -> trace.handler.onPreStream("end", chunk, false));
        endFrames.forEach(frame -> trace.handler.onPostStream("end", frame));
        trace.handler.onCallDone("end", null);
        trace.handler.onCallDone("test", null);

        List<TraceWorkflowSpan> spans = trace.drain();
        assertStatusSequence(spans, List.of(
                "test:start",
                "start:start",
                "start:finish",
                "producer:start",
                "producer:finish",
                "end:start",
                "end:finish",
                "test:finish"));

        TraceWorkflowSpan producer = lastSpan(spans, "producer");
        assertEquals(producerChunks, producer.getStreamOutputs());
        TraceWorkflowSpan end = lastSpan(spans, "end");
        assertEquals(producerChunks, end.getStreamInputs());
        assertEquals(endFrames, end.getStreamOutputs());
        assertEquals("test workflow", lastSpan(spans, "test").getWorkflowName());
    }

    @Test
    @DisplayName("test_seq_exec_stream_workflow_with_tracer")
    void testSeqExecStreamWorkflowWithTracer() {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        List<Map<String, Object>> customChunks = new ArrayList<>();

        runComponentWithTracer(trace, "a", List.of("start"),
                Map.of("aa", 1, "ac", 1),
                List.of(Map.of("node_id", "a", "id", 1, "data", "1"),
                        Map.of("node_id", "a", "id", 2, "data", "2")),
                customChunks);
        runComponentWithTracer(trace, "b", List.of("a"),
                Map.of("ba", 1, "bc", 1),
                List.of(Map.of("node_id", "b", "id", 1, "data", "1"),
                        Map.of("node_id", "b", "id", 2, "data", "2")),
                customChunks);

        List<TraceWorkflowSpan> spans = trace.drain();
        assertEquals(List.of(
                Map.of("node_id", "a", "id", 1, "data", "1"),
                Map.of("node_id", "a", "id", 2, "data", "2"),
                Map.of("node_id", "b", "id", 1, "data", "1"),
                Map.of("node_id", "b", "id", 2, "data", "2")), customChunks);
        assertTrue(lastSpan(spans, "a").getOnInvokeData().stream()
                .anyMatch(item -> String.valueOf(item.get("on_invoke_data")).contains("mock with")));
        assertEquals("a", lastSpan(spans, "b").getParentInvokeId());
    }

    @Test
    @DisplayName("test_parallel_exec_stream_workflow_with_tracer")
    void testParallelExecStreamWorkflowWithTracer() {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        List<Map<String, Object>> customChunks = new ArrayList<>();

        runComponentWithTracer(trace, "a", List.of("start"),
                Map.of("aa", 1, "ac", 1),
                List.of(Map.of("node_id", "a", "id", 1, "data", "1"),
                        Map.of("node_id", "a", "id", 2, "data", "2")),
                customChunks);
        runComponentWithTracer(trace, "b", List.of("start"),
                Map.of("ba", "haha", "bc", List.of(1, 2, 3)),
                List.of(Map.of("node_id", "b", "id", 1, "data", "1"),
                        Map.of("node_id", "b", "id", 2, "data", "2")),
                customChunks);

        List<TraceWorkflowSpan> spans = trace.drain();
        assertEquals(List.of("start"), lastSpan(spans, "a").getSourceIds());
        assertEquals(List.of("start"), lastSpan(spans, "b").getSourceIds());
        assertEquals(4, customChunks.size());
    }

    @Test
    @DisplayName("test_sub_stream_workflow_with_tracer")
    void testSubStreamWorkflowWithTracer() {
        WorkflowTraceHarness main = new WorkflowTraceHarness();
        WorkflowTraceHarness sub = new WorkflowTraceHarness(main.traceId, "a");

        main.handler.onCallStart("a", componentMetadata("main", "a"), Map.of("aa", 1), true, List.of("start"));
        sub.handler.onCallStart("sub_start", componentMetadata("sub", "sub_start"), Map.of("a", 1), true, null);
        sub.handler.onCallDone("sub_start", null);
        sub.handler.onCallStart("sub_a", componentMetadata("sub", "sub_a"), Map.of("aa", 1), true, List.of("sub_start"));
        sub.handler.onPostStream("sub_a", Map.of("node_id", "sub_start", "id", 1, "data", "1"));
        sub.handler.onCallDone("sub_a", null);
        sub.handler.onCallStart("sub_end", componentMetadata("sub", "sub_end"), Map.of("result", 1), true,
                List.of("sub_a"));
        sub.handler.onCallDone("sub_end", null);
        main.handler.onCallDone("a", Map.of("aa", 1));

        assertEquals("a", lastSpan(sub.drain(), "sub_end").getParentNodeId());
        assertEquals(NodeStatus.FINISH.getValue(), lastSpan(main.drain(), "a").getStatus());
    }

    @Test
    @DisplayName("test_nested_stream_workflow_with_tracer")
    void testNestedStreamWorkflowWithTracer() {
        WorkflowTraceHarness main = new WorkflowTraceHarness();
        WorkflowTraceHarness sub = new WorkflowTraceHarness(main.traceId, "a");

        main.handler.onCallStart("a", componentMetadata("main", "a"), Map.of("aa", 1), true, List.of("start"));
        main.handler.onCallStart("b", componentMetadata("main", "b"), Map.of("ba", "haha"), true, List.of("start"));
        main.handler.onPostStream("b", Map.of("node_id", "b", "id", 1, "data", "1"));
        main.handler.onCallDone("b", null);
        sub.handler.onCallStart("sub_a", componentMetadata("sub", "sub_a"), Map.of("aa", 1), true, null);
        sub.handler.onPostStream("sub_a", Map.of("node_id", "sub_start", "id", 1, "data", "1"));
        sub.handler.onCallDone("sub_a", null);
        main.handler.onCallDone("a", null);

        assertEquals("a", lastSpan(sub.drain(), "sub_a").getParentNodeId());
        assertEquals(List.of(Map.of("node_id", "b", "id", 1, "data", "1")),
                lastSpan(main.drain(), "b").getStreamOutputs());
    }

    @Test
    @DisplayName("test_nested_parallel_stream_workflow_with_tracer")
    void testNestedParallelStreamWorkflowWithTracer() {
        WorkflowTraceHarness main = new WorkflowTraceHarness();
        WorkflowTraceHarness subA = new WorkflowTraceHarness(main.traceId, "a");
        WorkflowTraceHarness subB = new WorkflowTraceHarness(main.traceId, "b");

        main.handler.onCallStart("a", componentMetadata("main", "a"), Map.of("aa", 1), true, List.of("start"));
        main.handler.onCallStart("b", componentMetadata("main", "b"), Map.of("aa", 1), true, List.of("start"));
        subA.handler.onCallStart("sub_a", componentMetadata("sub1", "sub_a"), Map.of("aa", 1), true, null);
        subA.handler.onCallDone("sub_a", null);
        subB.handler.onCallStart("sub_b", componentMetadata("sub2", "sub_b"), Map.of("aa", 1), true, null);
        subB.handler.onCallDone("sub_b", null);
        main.handler.onCallDone("a", null);
        main.handler.onCallDone("b", null);

        assertEquals("a", lastSpan(subA.drain(), "sub_a").getParentNodeId());
        assertEquals("b", lastSpan(subB.drain(), "sub_b").getParentNodeId());
        assertEquals(NodeStatus.FINISH.getValue(), lastSpan(main.drain(), "b").getStatus());
    }

    @Test
    @DisplayName("test_workflow_stream_with_loop_with_tracer")
    void testWorkflowStreamWithLoopWithTracer() {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        trace.handler.onCallStart("a", componentMetadata("test", "a"), Map.of("array", List.of(1, 2, 3)),
                true, List.of("s"));
        trace.handler.onCallDone("a", Map.of("array", List.of(1, 2, 3)));
        trace.handler.onCallStart("l", componentMetadata("test", "l"), Map.of("input_number", 1), true, List.of("a"));

        for (int loopIndex = 1; loopIndex <= 3; loopIndex++) {
            trace.handler.onCallStart("2", componentMetadata("test", "2"), Map.of("source", loopIndex),
                    true, List.of("1"));
            trace.handler.onCallDone("2", Map.of("result", loopIndex + 10));
            Map<String, Object> metadata = componentMetadata("test", "3");
            metadata.put("loop_node_id", "l");
            metadata.put("loop_index", loopIndex);
            trace.handler.onCallStart("3", metadata, Map.of("source", loopIndex), true, List.of("2"));
            trace.handler.onCallDone("3", Map.of("result", loopIndex + 10));
            trace.spanManager.popSpan("3");
            trace.spanManager.popSpan("2");
        }
        trace.handler.onCallDone("l", Map.of("results", List.of(11, 12, 13), "user_var", 31));

        int expectedLoopIndex = 1;
        for (TraceWorkflowSpan span : trace.drain()) {
            if ("l".equals(span.getInvokeId())) {
                assertEquals("a", span.getParentInvokeId());
                assertEquals("", span.getParentNodeId());
            } else if ("3".equals(span.getInvokeId()) && NodeStatus.FINISH.getValue().equals(span.getStatus())) {
                assertEquals("2", span.getParentInvokeId());
                assertEquals("", span.getParentNodeId());
                assertEquals("l", span.getLoopNodeId());
                assertEquals(expectedLoopIndex++, span.getLoopIndex());
            }
        }
        assertEquals(4, expectedLoopIndex);
    }

    @Test
    @DisplayName("test_workflow_stream_with_node_exception_with_tracer")
    void testWorkflowStreamWithNodeExceptionWithTracer() {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        trace.handler.onCallStart("end", componentMetadata("test", "end"), Map.of("end_input", 1), true,
                List.of("a"));
        trace.handler.onInvoke("end", null, ErrorHelper.buildError(
                StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                "reason", "mocked stream error",
                "workflow", "test"));

        TraceWorkflowSpan errorSpan = lastSpan(trace.drain(), "end");
        assertEquals(NodeStatus.ERROR.getValue(), errorSpan.getStatus());
        assertEquals(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode(), errorSpan.getError().get("error_code"));
        assertTrue(String.valueOf(errorSpan.getError().get("message")).contains("mocked stream error"));
    }

    @Test
    @DisplayName("test_interactive_workflow_with_trace")
    void testInteractiveWorkflowWithTrace() throws Exception {
        WorkflowTraceHarness interrupted = new WorkflowTraceHarness();
        interrupted.handler.onCallStart("test", workflowMetadata("test", "1.0", "test workflow"),
                Map.of("inputs", List.of(1, 2, 3)), true, null);
        interrupted.handler.onCallStart("interact", componentMetadata("test", "interact"),
                null, true, List.of("start"));
        interrupted.handler.onInvoke("interact", null, new GraphInterrupt());
        interrupted.handler.onCallDone("test", null);

        assertEquals(NodeStatus.INTERRUPTED.getValue(), lastSpan(interrupted.drain(), "interact").getStatus());

        WorkflowTraceHarness resumed = new WorkflowTraceHarness();
        resumed.handler.onCallStart("test", workflowMetadata("test", "1.0", "test workflow"),
                Map.of("user_inputs", Map.of(), "raw_inputs", "hello"), true, null);
        resumed.handler.onCallStart("interact", componentMetadata("test", "interact"), null, true, null);
        resumed.handler.onInteract("interact", "hello", componentMetadata("test", "interact"), true);
        resumed.handler.onPostInvoke("interact", Map.of("user_input", "hello"), null);
        resumed.handler.onCallDone("interact", Map.of("user_input", "hello"));
        resumed.handler.onCallStart("end", componentMetadata("test", "end"),
                Map.of("output", "hello"), true, List.of("interact"));
        resumed.handler.onPostStream("end",
                Map.of("type", "end node stream", "index", 0,
                        "payload", Map.of("output", Map.of("output", "hello"))));
        resumed.handler.onCallDone("end", null);
        resumed.handler.onCallDone("test", null);

        List<TraceWorkflowSpan> resumedSpans = resumed.drain();
        assertEquals("hello", lastSpan(resumedSpans, "interact").getInteractiveInputs());
        assertEquals(NodeStatus.FINISH.getValue(), lastSpan(resumedSpans, "end").getStatus());
    }

    @Test
    @DisplayName("test_sequence_interactive_workflow_with_trace")
    void testSequenceInteractiveWorkflowWithTrace() throws Exception {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        trace.handler.onCallStart("interact1", componentMetadata("test", "interact1"), null, true, List.of("start"));
        trace.handler.onInvoke("interact1", null, new GraphInterrupt());
        trace.handler.onCallStart("interact2", componentMetadata("test", "interact2"), null, true,
                List.of("interact1"));
        trace.handler.onInvoke("interact2", null, new GraphInterrupt());
        Map<String, Object> endMetadata = componentMetadata("test", "end");
        endMetadata.put("component_type", "End");
        trace.handler.onCallStart("end", endMetadata, Map.of("output", "hello2"), true, List.of("interact2"));
        trace.handler.onCallDone("end", null);

        List<TraceWorkflowSpan> spans = trace.drain();
        assertEquals(NodeStatus.INTERRUPTED.getValue(), lastSpan(spans, "interact1").getStatus());
        assertEquals(NodeStatus.INTERRUPTED.getValue(), lastSpan(spans, "interact2").getStatus());
        assertNotNull(lastSpan(spans, "end").getComponentType());
    }

    @Test
    @DisplayName("test_workflow_with_branch_with_tracer")
    void testWorkflowWithBranchWithTracer() {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        List<Map<String, Object>> branches = List.of(
                Map.of("branch_id", "1", "condition",
                        Map.of("bool_expression", "${a} <= 10", "inputs", Map.of("${a}", 2))),
                Map.of("branch_id", "2", "condition",
                        Map.of("bool_expression", "${a} > 10", "inputs", Map.of("${a}", 2))));
        Map<String, Object> branchInputs = Map.of("branches", branches);

        trace.handler.onCallStart("sw", componentMetadata("test", "sw"), branchInputs, true, List.of("start"));

        TraceWorkflowSpan branchSpan = lastSpan(trace.drain(), "sw");
        assertNotNull(branchSpan.getStartTime());
        assertEquals(branchInputs, branchSpan.getInputs());
    }

    @Test
    @DisplayName("test_sequence_interactive_workflow_reuse_with_trace")
    void testSequenceInteractiveWorkflowReuseWithTrace() throws Exception {
        WorkflowTraceHarness trace = new WorkflowTraceHarness();
        trace.handler.onCallStart("interact1", componentMetadata("test", "interact1"), null, true, List.of("start"));
        trace.handler.onInvoke("interact1", null, new GraphInterrupt());
        trace.handler.onCallStart("interact2", componentMetadata("test", "interact2"), null, true,
                List.of("interact1"));
        trace.handler.onInteract("interact2", "hello1", componentMetadata("test", "interact2"), true);
        trace.handler.onInvoke("interact2", null, new GraphInterrupt());
        trace.handler.onCallStart("end", componentMetadata("test", "end"), Map.of("output", "hello2"),
                true, List.of("interact2"));
        trace.handler.onCallDone("end", null);

        List<TraceWorkflowSpan> spans = trace.drain();
        assertEquals(NodeStatus.INTERRUPTED.getValue(), lastSpan(spans, "interact1").getStatus());
        assertEquals("hello1", lastSpan(spans, "interact2").getInteractiveInputs());
        assertNotNull(lastSpan(spans, "end").getComponentType());
    }

    @Test
    @DisplayName("test tracer creation")
    void testTracerCreation() {
        Tracer tracer = new Tracer();
        assertNotNull(tracer.getTraceId());
        assertNotNull(tracer.getTracerAgentSpanManager());
    }

    @Test
    @DisplayName("test workflow span creation and retrieval")
    void testWorkflowSpanCreation() {
        String traceId = UUID.randomUUID().toString();
        SpanManager spanManager = new SpanManager(traceId, "workflow_node");
        TraceWorkflowSpan span = spanManager.createWorkflowSpan("invoke", null);

        assertEquals("invoke", span.getInvokeId());
        assertEquals(traceId, span.getTraceId());
        assertEquals("workflow_node", span.getParentNodeId());
        assertEquals(span, spanManager.getSpan("invoke"));
    }

    private static void runComponentWithTracer(WorkflowTraceHarness trace, String componentId,
                                               List<String> sourceIds, Map<String, Object> inputs,
                                               List<Map<String, Object>> chunks,
                                               List<Map<String, Object>> customChunks) {
        trace.handler.onCallStart(componentId, componentMetadata("test", componentId), inputs, true, sourceIds);
        trace.handler.onInvoke(componentId, Map.of("on_invoke_data", "mock with " + inputs), null);
        for (Map<String, Object> chunk : chunks) {
            customChunks.add(chunk);
            trace.handler.onPostStream(componentId, chunk);
        }
        trace.handler.onCallDone(componentId, inputs);
    }

    private static void assertStatusSequence(List<TraceWorkflowSpan> spans, List<String> expected) {
        List<String> actual = spans.stream()
                .map(span -> span.getInvokeId() + ":" + span.getStatus())
                .toList();
        assertEquals(expected, actual);
    }

    private static TraceWorkflowSpan lastSpan(List<TraceWorkflowSpan> spans, String invokeId) {
        for (int i = spans.size() - 1; i >= 0; i--) {
            TraceWorkflowSpan span = spans.get(i);
            if (invokeId.equals(span.getInvokeId())) {
                return span;
            }
        }
        throw new AssertionError("span not found: " + invokeId);
    }

    private static Map<String, Object> workflowMetadata(String id, String version, String name) {
        return mapWithNulls(
                "workflow_id", id,
                "workflow_version", version,
                "workflow_name", name);
    }

    private static Map<String, Object> componentMetadata(String workflowId, String componentId) {
        return mapWithNulls(
                "workflow_id", workflowId,
                "component_id", componentId,
                "component_name", componentId,
                "component_type", componentId);
    }

    private static Map<String, Object> mapWithNulls(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private enum NullValue {
        INSTANCE
    }

    private static final class WorkflowTraceHarness {
        private final String traceId;
        private final SpanManager spanManager;
        private final StreamEmitter emitter;
        private final StreamWriterManager writerManager;
        private final TraceWorkflowHandler handler;

        private WorkflowTraceHarness() {
            this(UUID.randomUUID().toString(), "");
        }

        private WorkflowTraceHarness(String traceId, String parentNodeId) {
            this.traceId = traceId;
            this.spanManager = new SpanManager(traceId, parentNodeId);
            this.emitter = new StreamEmitter();
            this.writerManager = new StreamWriterManager(emitter, List.of(StreamMode.TRACE));
            this.handler = new TraceWorkflowHandler(new Object(), writerManager, spanManager);
        }

        private List<TraceWorkflowSpan> drain() {
            emitter.close();
            Iterator<Object> iterator = writerManager.streamIterator(1_000, 1_000, true);
            List<TraceWorkflowSpan> spans = new ArrayList<>();
            while (iterator.hasNext()) {
                TraceSchema schema = assertInstanceOf(TraceSchema.class, iterator.next());
                spans.add(assertInstanceOf(TraceWorkflowSpan.class, schema.getPayload()));
            }
            return spans;
        }
    }
}
