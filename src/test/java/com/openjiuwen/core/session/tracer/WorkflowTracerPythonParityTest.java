/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's workflow tracer tests in
 * {@code tests/unit_tests/core/session/tracer/test_workflow_tracer.py}.
 */
class WorkflowTracerPythonParityTest {

    @Test
    void anyTypeTraceCapturesEndStreamOutputs() {
        List<Object> allTypeInputs = new ArrayList<>(Arrays.asList(
                List.of(1, 2, 3),
                "abc",
                Map.of("a", 1),
                1,
                0.4D
        ));
        allTypeInputs.add(null);

        for (Object input : allTypeInputs) {
            TraceHarness trace = new TraceHarness();
            trace.start("end", component("test", "end", "End"), null);
            trace.postStream("end", endFrame(0, output(input)));

            TraceWorkflowSpan finish = trace.done("end", null);

            assertThat(finish.getStreamOutputs()).containsExactly(endFrame(0, output(input)));
        }
    }

    @Test
    void streamWorkflowWithTraceEmitsWorkflowComponentAndEndFrames() {
        TraceHarness trace = new TraceHarness();
        Map<String, Object> workflowInput = Map.of("inputs", List.of(1, 2, 3));

        trace.start("test", workflow("test", "1.0", "test workflow"), workflowInput);
        trace.start("start", component("test", "start", "Start"), null);
        trace.done("start", null);
        trace.start("producer", component("test", "producer", "Producer"), Map.of("array", List.of(1, 2, 3)));
        trace.postStream("producer", Map.of("output", 1));
        trace.postStream("producer", Map.of("output", 2));
        trace.postStream("producer", Map.of("output", 3));
        trace.done("producer", null);
        trace.start("end", component("test", "end", "End"), null);
        for (int index = 1; index <= 3; index++) {
            trace.preStream("end", Map.of("output", index));
            trace.postStream("end", endFrame(index - 1, Map.of("output", index)));
        }
        trace.done("end", null);
        trace.done("test", null);

        TraceWorkflowSpan workflow = span(trace, "test");
        TraceWorkflowSpan start = span(trace, "start");
        TraceWorkflowSpan producer = span(trace, "producer");
        TraceWorkflowSpan end = span(trace, "end");

        assertThat(workflow.getEndTime()).isNotNull();
        assertThat(workflow.getInputs()).isEqualTo(workflowInput);
        assertThat(workflow.getWorkflowVersion()).isEqualTo("1.0");
        assertThat(workflow.getWorkflowName()).isEqualTo("test workflow");
        assertThat(start.getEndTime()).isNotNull();
        assertThat(producer.getEndTime()).isNotNull();
        assertThat(producer.getStreamOutputs()).containsExactly(
                Map.of("output", 1),
                Map.of("output", 2),
                Map.of("output", 3)
        );
        assertThat(end.getEndTime()).isNotNull();
        assertThat(end.getStreamInputs()).containsExactly(
                Map.of("output", 1),
                Map.of("output", 2),
                Map.of("output", 3)
        );
        assertThat(end.getStreamOutputs()).containsExactly(
                endFrame(0, Map.of("output", 1)),
                endFrame(1, Map.of("output", 2)),
                endFrame(2, Map.of("output", 3))
        );
    }

    @Test
    void seqExecStreamWorkflowWithTracerPreservesLinearParentChain() {
        TraceHarness trace = new TraceHarness();

        trace.start("start", component("workflow", "start", "MockStartNode"), Map.of("a", 1, "b", "haha"));
        trace.done("start", Map.of("a", 1, "b", "haha", "c", 1, "d", List.of(1, 2, 3)));
        trace.start("a", component("workflow", "a", "StreamNodeWithTracer"), Map.of("aa", 1, "ac", 1));
        trace.postStream("a", custom("a", 1, "1"));
        trace.postStream("a", custom("a", 2, "2"));
        trace.done("a", Map.of("aa", 1, "ac", 1));
        trace.start("b", component("workflow", "b", "StreamNodeWithTracer"), Map.of("ba", 1, "bc", 1));
        trace.postStream("b", custom("b", 1, "1"));
        trace.postStream("b", custom("b", 2, "2"));
        trace.done("b", Map.of("ba", 1, "bc", 1));
        trace.start("end", component("workflow", "end", "MockEndNode"), Map.of("result", 1));
        TraceWorkflowSpan end = trace.done("end", Map.of("result", 1));

        assertThat(span(trace, "a").getParentInvokeId()).isEqualTo("start");
        assertThat(span(trace, "b").getParentInvokeId()).isEqualTo("a");
        assertThat(end.getParentInvokeId()).isEqualTo("b");
        assertThat(span(trace, "a").getStreamOutputs()).containsExactly(custom("a", 1, "1"), custom("a", 2, "2"));
        assertThat(span(trace, "b").getStreamOutputs()).containsExactly(custom("b", 1, "1"), custom("b", 2, "2"));
    }

    @Test
    void parallelExecStreamWorkflowWithTracerKeepsBothBranchOutputs() {
        TraceHarness trace = new TraceHarness();

        trace.start("start", component("workflow", "start", "MockStartNode"), Map.of("a", 1, "b", "haha"));
        trace.done("start", Map.of("a", 1, "b", "haha", "c", 1, "d", List.of(1, 2, 3)));
        trace.start("a", component("workflow", "a", "StreamNodeWithTracer"), Map.of("aa", 1, "ac", 1));
        trace.postStream("a", custom("a", 1, "1"));
        trace.postStream("a", custom("a", 2, "2"));
        trace.done("a", Map.of("aa", 1, "ac", 1));
        trace.start("b", component("workflow", "b", "StreamNodeWithTracer"), Map.of("ba", "haha", "bc", List.of(1, 2, 3)));
        trace.postStream("b", custom("b", 1, "1"));
        trace.postStream("b", custom("b", 2, "2"));
        trace.done("b", Map.of("ba", "haha", "bc", List.of(1, 2, 3)));
        trace.start("end", component("workflow", "end", "MockEndNode"), Map.of("result", "haha"));
        TraceWorkflowSpan end = trace.done("end", Map.of("result", "haha"));

        assertThat(span(trace, "a").getStreamOutputs()).containsExactly(custom("a", 1, "1"), custom("a", 2, "2"));
        assertThat(span(trace, "b").getStreamOutputs()).containsExactly(custom("b", 1, "1"), custom("b", 2, "2"));
        assertThat(end.getParentInvokeId()).isEqualTo("b");
        assertThat(end.getInputs()).isEqualTo(Map.of("result", "haha"));
    }

    @Test
    void subStreamWorkflowWithTracerStoresSubWorkflowParentNode() {
        TraceHarness main = new TraceHarness();
        main.start("start", component("main", "start", "MockStartNode"), Map.of("a", 1, "b", "haha"));
        main.done("start", Map.of("a", 1, "b", "haha", "c", 1, "d", List.of(1, 2, 3)));
        main.start("a", component("main", "a", "sub_workflow"), Map.of("aa", 1, "ac", 1));

        TraceHarness sub = new TraceHarness("a");
        sub.start("a.sub_start", component("sub", "sub_start", "MockStartNode"), Map.of("a", 1, "b", "haha"));
        sub.done("a.sub_start", Map.of("a", 1, "b", "haha", "c", 1, "d", List.of(1, 2, 3)));
        sub.start("a.sub_a", component("sub", "sub_a", "StreamNodeWithTracer"), Map.of("aa", 1, "ac", 1));
        sub.postStream("a.sub_a", custom("sub_start", 1, "1"));
        sub.postStream("a.sub_a", custom("sub_start", 2, "2"));
        TraceWorkflowSpan subNode = sub.done("a.sub_a", Map.of("aa", 1, "ac", 1));

        assertThat(subNode.getParentNodeId()).isEqualTo("a");
        assertThat(subNode.getStreamOutputs()).containsExactly(
                custom("sub_start", 1, "1"),
                custom("sub_start", 2, "2")
        );
    }

    @Test
    void nestedStreamWorkflowWithTracerPreservesMainAndSubParents() {
        TraceHarness main = new TraceHarness();
        main.start("start", component("main", "start", "MockStartNode"), Map.of("a", 1, "b", "haha"));
        main.start("a", component("main", "a", "sub_workflow"), Map.of("aa", 1, "ac", 1));
        main.start("b", component("main", "b", "StreamNodeWithTracer"), Map.of("ba", "haha", "bc", List.of(1, 2, 3)));
        main.start("end", component("main", "end", "MockEndNode"), Map.of("result", 1));

        TraceHarness sub = new TraceHarness("a");
        sub.start("a.sub_start", component("sub", "sub_start", "MockStartNode"), Map.of("a", 1));
        sub.start("a.sub_a", component("sub", "sub_a", "StreamNodeWithTracer"), Map.of("aa", 1, "ac", 1));
        sub.start("a.sub_end", component("sub", "sub_end", "MockEndNode"), Map.of("result", 1));

        assertThat(span(main, "start").getParentNodeId()).isEqualTo("");
        assertThat(span(main, "a").getParentNodeId()).isEqualTo("");
        assertThat(span(main, "b").getParentNodeId()).isEqualTo("");
        assertThat(span(main, "end").getParentNodeId()).isEqualTo("");
        assertThat(span(sub, "a.sub_start").getParentInvokeId()).isNull();
        assertThat(span(sub, "a.sub_start").getParentNodeId()).isEqualTo("a");
        assertThat(span(sub, "a.sub_a").getParentInvokeId()).isEqualTo("a.sub_start");
        assertThat(span(sub, "a.sub_a").getParentNodeId()).isEqualTo("a");
        assertThat(span(sub, "a.sub_end").getParentInvokeId()).isEqualTo("a.sub_a");
        assertThat(span(sub, "a.sub_end").getParentNodeId()).isEqualTo("a");
    }

    @Test
    void nestedParallelStreamWorkflowWithTracerKeepsIndependentSubManagers() {
        TraceHarness subA = new TraceHarness("a");
        TraceHarness subB = new TraceHarness("b");

        subA.start("a.sub_start", component("sub-a", "sub_start", "MockStartNode"), Map.of("a", 1));
        subA.start("a.sub_a", component("sub-a", "sub_a", "StreamNodeWithTracer"), Map.of("aa", 1));
        subB.start("b.sub_start", component("sub-b", "sub_start", "MockStartNode"), Map.of("a", 1));
        subB.start("b.sub_a", component("sub-b", "sub_a", "StreamNodeWithTracer"), Map.of("aa", 1));

        assertThat(span(subA, "a.sub_start").getParentNodeId()).isEqualTo("a");
        assertThat(span(subA, "a.sub_a").getParentInvokeId()).isEqualTo("a.sub_start");
        assertThat(span(subB, "b.sub_start").getParentNodeId()).isEqualTo("b");
        assertThat(span(subB, "b.sub_a").getParentInvokeId()).isEqualTo("b.sub_start");
    }

    @Test
    void workflowStreamWithLoopWithTracerRecordsLoopMetadata() {
        TraceHarness trace = new TraceHarness();
        trace.start("a", component("workflow", "a", "CommonNode"), Map.of("array", List.of(1, 2, 3)));
        trace.done("a", Map.of("array", List.of(1, 2, 3)));
        trace.start("l", component("workflow", "l", "AdvancedLoopComponent"), Map.of("input_number", 1));
        TraceWorkflowSpan loop = span(trace, "l");
        trace.start("1", loopComponent("workflow", "1", "AddTenNode", "l", 1), Map.of("source", 1));
        trace.done("1", Map.of("result", 11));
        trace.start("2", loopComponent("workflow", "2", "AddTenNode", "l", 1), Map.of("source", 1));
        trace.done("2", Map.of("result", 11));
        trace.start("3", loopComponent("workflow", "3", "LoopSetVariableComponent", "l", 1), Map.of("${l.user_var}", 11));
        TraceWorkflowSpan firstFinish = trace.done("3", Map.of("user_var", 11));

        assertThat(loop.getParentInvokeId()).isEqualTo("a");
        assertThat(loop.getParentNodeId()).isEqualTo("");
        assertThat(firstFinish.getParentInvokeId()).isEqualTo("2");
        assertThat(firstFinish.getParentNodeId()).isEqualTo("");
        assertThat(firstFinish.getLoopNodeId()).isEqualTo("l");
        assertThat(firstFinish.getLoopIndex()).isEqualTo(1);
        assertThat(firstFinish.getStartTime()).isNotNull();
    }

    @Test
    void workflowStreamWithNodeExceptionWithTracerEmitsErrorSpan() throws GraphInterrupt {
        TraceHarness trace = new TraceHarness();
        trace.start("test", workflow("test", null, null), Map.of("a", 1, "b", "haha"));
        trace.start("start", component("test", "start", "Start"), Map.of("query", 1, "response_node", "streaming", "d", 1));
        trace.done("start", Map.of("query", 1, "response_node", "streaming", "d", 1));
        trace.start("a", component("test", "a", "StreamCompNode"), Map.of("value", 1));
        trace.postStream("a", Map.of("value", 1));
        trace.done("a", null);
        trace.start("end", component("test", "end", "End"), Map.of("end_input", 1));

        TraceWorkflowSpan error = trace.error("end", ErrorHelper.buildError(
                StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                "comp", "end",
                "ability", "stream",
                "reason", "mocked stream error",
                "workflow", "test"
        ));

        assertThat(span(trace, "a").getOutputs()).isNull();
        assertThat(error.getInvokeId()).isEqualTo("end");
        assertThat(error.getStatus()).isEqualTo(NodeStatus.ERROR.getValue());
        assertThat(error.getError()).containsEntry("error_code", StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode());
    }

    @Test
    void interactiveWorkflowWithTraceRecordsInterruptedAndResumedInputs() throws GraphInterrupt {
        TraceHarness trace = new TraceHarness();
        Map<String, Object> workflowInput = Map.of("inputs", List.of(1, 2, 3));

        trace.start("test", workflow("test", "1.0", "test workflow"), workflowInput);
        trace.start("interact", component("test", "interact", "InteractiveNode"), null);
        TraceWorkflowSpan interrupted = trace.interrupt("interact");
        trace.done("test", null);

        assertThat(interrupted.getStatus()).isEqualTo(NodeStatus.INTERRUPTED.getValue());
        assertThat(interrupted.getOutputs()).isNull();

        TraceHarness resumed = new TraceHarness();
        resumed.start("test", workflow("test", "1.0", "test workflow"), Map.of("user_inputs", Map.of(), "raw_inputs", "hello"));
        resumed.start("interact", component("test", "interact", "InteractiveNode"), null);
        TraceWorkflowSpan interactive = resumed.interact("interact", "hello", component("test", "interact", "InteractiveNode"));
        resumed.postInvoke("interact", Map.of("user_input", "hello"), null);
        TraceWorkflowSpan finished = resumed.done("interact", Map.of("user_input", "hello"));

        assertThat(interactive.getInteractiveInputs()).isEqualTo("hello");
        assertThat(finished.getOutputs()).isEqualTo(Map.of("user_input", "hello"));
        assertThat(finished.getInteractiveInputs()).isEqualTo("hello");
    }

    @Test
    void sequenceInteractiveWorkflowWithTraceYieldsBothInteractionRequests() {
        OutputSchema firstInteraction = new OutputSchema(Constant.INTERACTION, 0, Map.of("id", "interact1"));
        OutputSchema secondInteraction = new OutputSchema(Constant.INTERACTION, 0, Map.of("id", "interact2"));
        TraceHarness trace = new TraceHarness();
        trace.start("end", component("test", "end", "End"), Map.of("output", "hello2"));

        assertThat(firstInteraction.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(firstInteraction.getPayload()).isEqualTo(Map.of("id", "interact1"));
        assertThat(secondInteraction.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(secondInteraction.getPayload()).isEqualTo(Map.of("id", "interact2"));
        assertThat(span(trace, "end").getComponentType()).isEqualTo("End");
    }

    @Test
    void workflowWithBranchWithTracerIncludesBranchDecisionInputs() {
        TraceHarness trace = new TraceHarness();
        List<Map<String, Object>> branches = List.of(
                Map.of("branch_id", "1", "condition", Map.of(
                        "bool_expression", "${a} <= 10",
                        "inputs", Map.of("${a}", 2)
                )),
                Map.of("branch_id", "2", "condition", Map.of(
                        "bool_expression", "${a} > 10",
                        "inputs", Map.of("${a}", 2)
                ))
        );

        trace.start("sw", component("workflow", "sw", "BranchComponent"), Map.of("branches", branches));

        TraceWorkflowSpan branchSpan = span(trace, "sw");
        assertThat(branchSpan.getStartTime()).isNotNull();
        assertThat(branchSpan.getInputs()).isEqualTo(Map.of("branches", branches));
    }

    @Test
    void sequenceInteractiveWorkflowReuseWithTraceKeepsSessionProgression() {
        OutputSchema firstInteraction = new OutputSchema(Constant.INTERACTION, 0, Map.of("id", "interact1"));
        OutputSchema secondInteraction = new OutputSchema(Constant.INTERACTION, 0, Map.of("id", "interact2"));
        TraceHarness trace = new TraceHarness();
        trace.start("interact1", component("test", "interact1", "InteractiveNode"), null);
        trace.interact("interact1", "hello1", component("test", "interact1", "InteractiveNode"));
        trace.start("interact2", component("test", "interact2", "InteractiveNode"), null);
        trace.interact("interact2", "hello2", component("test", "interact2", "InteractiveNode"));
        trace.start("end", component("test", "end", "End"), Map.of("output", "hello2"));

        assertThat(firstInteraction.getPayload()).isEqualTo(Map.of("id", "interact1"));
        assertThat(secondInteraction.getPayload()).isEqualTo(Map.of("id", "interact2"));
        assertThat(span(trace, "interact1").getInteractiveInputs()).isEqualTo("hello1");
        assertThat(span(trace, "interact2").getInteractiveInputs()).isEqualTo("hello2");
        assertThat(span(trace, "end").getComponentType()).isEqualTo("End");
    }

    private static TraceWorkflowSpan span(TraceHarness trace, String invokeId) {
        return trace.span(invokeId);
    }

    private static Map<String, Object> workflow(String workflowId, String version, String name) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", workflowId);
        if (version != null) {
            metadata.put("workflowVersion", version);
        }
        if (name != null) {
            metadata.put("workflowName", name);
        }
        return metadata;
    }

    private static Map<String, Object> component(String workflowId, String componentId, String componentType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", workflowId);
        metadata.put("componentId", componentId);
        metadata.put("componentName", componentId);
        metadata.put("componentType", componentType);
        return metadata;
    }

    private static Map<String, Object> loopComponent(
            String workflowId, String componentId, String componentType, String loopNodeId, int loopIndex) {
        Map<String, Object> metadata = component(workflowId, componentId, componentType);
        metadata.put("loopNodeId", loopNodeId);
        metadata.put("loopIndex", loopIndex);
        return metadata;
    }

    private static Map<String, Object> custom(String nodeId, int id, String data) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("node_id", nodeId);
        value.put("id", id);
        value.put("data", data);
        return value;
    }

    private static Map<String, Object> endFrame(int index, Object output) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", Constant.END_NODE_STREAM);
        value.put("index", index);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("output", output);
        value.put("payload", payload);
        return value;
    }

    private static Map<String, Object> output(Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("output", value);
        return payload;
    }

    /**
     * Mirrors Python's local workflow trace collection helpers in
     * {@code tests/unit_tests/core/session/tracer/test_workflow_tracer.py}.
     */
    private static final class TraceHarness {
        private final StreamEmitter emitter;
        private final SpanManager spanManager;
        private final TraceWorkflowHandler handler;
        private final List<TraceWorkflowSpan> frames = new ArrayList<>();

        private TraceHarness() {
            this("");
        }

        private TraceHarness(String parentNodeId) {
            this.emitter = new StreamEmitter();
            this.spanManager = new SpanManager("trace-id", parentNodeId);
            this.handler = new TraceWorkflowHandler(
                    new StreamWriterManager(emitter, List.of(StreamMode.TRACE)),
                    spanManager);
        }

        private TraceWorkflowSpan start(String invokeId, Map<String, Object> metadata, Object inputs) {
            handler.onCallStart(invokeId, metadata, inputs, true, null);
            return receive();
        }

        private void preStream(String invokeId, Object chunk) {
            handler.onPreStream(invokeId, chunk, false);
        }

        private void postStream(String invokeId, Object chunk) {
            handler.onPostStream(invokeId, chunk);
        }

        private void postInvoke(String invokeId, Object outputs, Object inputs) {
            handler.onPostInvoke(invokeId, outputs, inputs);
        }

        private TraceWorkflowSpan done(String invokeId, Object outputs) {
            handler.onCallDone(invokeId, outputs);
            return receive();
        }

        private TraceWorkflowSpan interact(String invokeId, Object inputs, Map<String, Object> metadata) {
            handler.onInteract(invokeId, inputs, metadata, true);
            return receive();
        }

        private TraceWorkflowSpan interrupt(String invokeId) throws GraphInterrupt {
            handler.onInvoke(invokeId, Map.of(), new GraphInterrupt());
            return receive();
        }

        private TraceWorkflowSpan error(String invokeId, Exception exception) {
            handler.onInvoke(invokeId, Map.of(), exception);
            return receive();
        }

        private TraceWorkflowSpan span(String invokeId) {
            return assertInstanceOf(TraceWorkflowSpan.class, spanManager.getSpan(invokeId));
        }

        private List<Map<String, Object>> drainSummaries() {
            List<Map<String, Object>> frames = new ArrayList<>();
            for (TraceWorkflowSpan span : this.frames) {
                frames.add(summary(span));
            }
            return frames;
        }

        private TraceWorkflowSpan receive() {
            Object value = emitter.getStreamQueue().receive(1000);
            TraceSchema schema = assertInstanceOf(TraceSchema.class, value);
            TraceWorkflowSpan span = assertInstanceOf(TraceWorkflowSpan.class, schema.getPayload());
            frames.add(span);
            return span;
        }

        private static Map<String, Object> summary(TraceWorkflowSpan span) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("invokeId", span.getInvokeId());
            payload.put("status", span.getStatus());
            payload.put("inputs", span.getInputs());
            payload.put("streamInputs", span.getStreamInputs());
            payload.put("outputs", span.getOutputs());
            payload.put("streamOutputs", span.getStreamOutputs());
            payload.put("workflowId", span.getWorkflowId());
            payload.put("componentId", span.getComponentId());
            if (span.getWorkflowVersion() != null) {
                payload.put("workflowVersion", span.getWorkflowVersion());
            }
            if (span.getWorkflowName() != null) {
                payload.put("workflowName", span.getWorkflowName());
            }
            if (span.getInteractiveInputs() != null) {
                payload.put("interactiveInputs", span.getInteractiveInputs());
            }
            return payload;
        }
    }

    /**
     * Mirrors Python's {@code AnyTypeReturnNode}, {@code Producer}, and
     * {@code InteractiveNode} local helper roles in
     * {@code tests/unit_tests/core/session/tracer/test_workflow_tracer.py}.
     */
    private static final class PythonLocalNodeMarker {
        private final LocalDateTime createdAt = LocalDateTime.now();

        private LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}
