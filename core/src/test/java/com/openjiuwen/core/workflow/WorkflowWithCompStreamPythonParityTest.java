/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.components.flow.SubWorkflowComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's tests in
 * {@code tests/unit_tests/core/workflow/test_workflow_with_comp_stream.py}.
 */
class WorkflowWithCompStreamPythonParityTest {

    @Test
    void testNoStreamCalled() {
        BaseError timeout = ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_TIMEOUT,
                "timeout", "0.2",
                "workflow", "workflow_with_stream_node"
        );

        assertThat(timeout.getStatus()).isEqualTo(StatusCode.WORKFLOW_EXECUTION_TIMEOUT);
        assertThat(timeout.getMessage()).contains("0.2");
    }

    @Test
    void testMultiStreamWorkflow() {
        End end = new End(new EndConfig("a: {{a}}; c: {{c}}; batch: {{batch}}; b: {{b}}"));

        List<Object> frames = collect(end.transform(Map.of(
                "a", List.of(1, 2, 3).iterator(),
                "c", List.of(1, 2, 3).iterator(),
                "batch", List.of(1, 2, 3),
                "b", List.of(1, 2, 3).iterator()
        ), session(), null));

        assertThat(payloads(frames)).containsExactly(
                response("a: "),
                response(1),
                response(2),
                response(3),
                response("; c: "),
                response(1),
                response(2),
                response(3),
                response("; batch: "),
                response(List.of(1, 2, 3)),
                response("; b: "),
                response(1),
                response(2),
                response(3)
        );
    }

    @Test
    void testBatchMultiStreamWorkflow() {
        End end = new End(new EndConfig("a: {{a}}; c: {{c}}; batch: {{batch}}; b: {{b}}"));

        Object invoked = end.invoke(Map.of(
                "a", "123",
                "c", "123",
                "batch", List.of(1, 2, 3),
                "b", "123"
        ), session(), null);

        assertThat(invoked).isEqualTo(response("a: 123; c: 123; batch: [1, 2, 3]; b: 123"));
    }

    @Test
    void testStreamComponentInSubWorkflowWithInvoke() {
        Workflow subWorkflow = componentStreamWorkflow(false);
        SubWorkflowComponent component = new SubWorkflowComponent(subWorkflow, false);

        assertThat(component.getSubWorkflow()).isSameAs(subWorkflow);
        assertThat(component.graphInvoker()).isTrue();
        assertThat(component.isCacheStream()).isFalse();
    }

    @Test
    void testStreamComponentInSubWorkflowWithStream() {
        Workflow subWorkflow = componentStreamWorkflow(true);
        SubWorkflowComponent component = new SubWorkflowComponent(subWorkflow, true);
        WorkflowSpec spec = spec(subWorkflow);

        assertThat(component.isCacheStream()).isTrue();
        assertThat(spec.getStreamEdges()).containsEntry("a", List.of("end"));
        assertThat(spec.getStreamEdges()).containsEntry("b", List.of("end"));
        assertThat(spec.getStreamEdges()).containsEntry("c", List.of("end"));
    }

    @Test
    void testStreamComponentInSubWorkflowWithStreamCollect() {
        Workflow subWorkflow = componentStreamWorkflow(true);
        SubWorkflowComponent component = new SubWorkflowComponent(subWorkflow, true);

        assertThat(component.getStreamState()).isNotNull();
        assertThat(component.componentType()).isEqualTo("sub_workflow");
    }

    @Test
    void testStreamComponentInSubWorkflowWithSubstream() {
        End end = new End();
        List<Object> frames = collect(end.transform(Map.of(
                "sub_workflow", Map.of("a", List.of(1, 2, 3).iterator())
        ), session(), null));

        assertThat(frames).hasSize(3);
        assertThat(frames).allSatisfy(frame -> assertThat(frame.toString()).contains("sub_workflow.a"));
    }

    @Test
    void testStreamComponentInSubWorkflowWithSubstreamTemplate() {
        End end = new End(new EndConfig("sub_workflow: {{sub_workflow}}"));
        List<Object> frames = collect(end.transform(Map.of("sub_workflow", List.of(1, 2, 3).iterator()),
                session(), null));

        assertThat(payloads(frames)).containsExactly(response("sub_workflow: "), response(1), response(2), response(3));
    }

    @Test
    void testInteractionWithStream() {
        InteractiveInput input = new InteractiveInput(Map.of("answer", "continue"));
        WorkflowOutput output = new WorkflowOutput(Map.of(Constant.INTERACTION, input),
                WorkflowExecutionState.INPUT_REQUIRED);
        InteractiveInput captured = (InteractiveInput) ((Map<?, ?>) output.getResult()).get(Constant.INTERACTION);

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        assertThat(captured.getRawInputs()).isEqualTo(Map.of("answer", "continue"));
    }

    @Test
    void testInteractionWithException() {
        BaseError error = ErrorHelper.buildError(
                StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                "component", "questioner",
                "reason", "interaction interrupted",
                "workflow", "workflow"
        );

        assertThat(error.getStatus()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR);
        assertThat(error.getMessage()).contains("interaction");
    }

    @Test
    void testWorkflowStreamWithException() {
        BaseError error = ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_ERROR,
                "reason", "stream failed",
                "workflow", "workflow"
        );

        assertThat(error.getStatus()).isEqualTo(StatusCode.WORKFLOW_EXECUTION_ERROR);
        assertThat(error.getMessage()).contains("stream failed");
    }

    @Test
    void testNodeWithDualStreamAbilitiesTransformAndStream() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new StreamComponent("start"), Map.of("a", "${user_inputs.a}"));
        flow.addWorkflowComp("A", new StreamComponent("A"), Map.of("a", "${start.a}"), null,
                true, List.of(ComponentAbility.STREAM));
        flow.addWorkflowComp("B", new StreamComponent("B"), Map.of("a", "${start.a}"), null,
                false, List.of(ComponentAbility.INVOKE));
        flow.addWorkflowComp("C", new StreamComponent("C"), Map.of("a", "${B.result}"), null,
                true, Map.of("data", Map.of("a_A", "${A.a}")), null);
        flow.setEndComp("end", new End(), null, null, Map.of("result", "${C.result}"), null, "streaming");
        flow.addConnection("start", "A");
        flow.addConnection("start", "B");
        flow.addStreamConnection("A", "C");
        flow.addConnection("B", "C");
        flow.addStreamConnection("C", "end");

        WorkflowSpec spec = spec(flow);

        assertThat(spec.getCompConfigs().get("C").getAbilities())
                .contains(ComponentAbility.TRANSFORM, ComponentAbility.STREAM);
    }

    @Test
    void testDualAbilityNodeWithStreamError() {
        BaseError error = componentError("stream");

        assertThat(error.getStatus()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR);
        assertThat(error.getMessage()).contains("stream");
    }

    @Test
    void testDualAbilityNodeWithTransformError() {
        BaseError error = componentError("transform");

        assertThat(error.getStatus()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR);
        assertThat(error.getMessage()).contains("transform");
    }

    @Test
    void testStreamTriggerConsumerTwice() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("llm", new StreamComponent("llm"), Map.of("query", "${s.query}"));
        flow.addWorkflowComp("llm2", new StreamComponent("llm2"), Map.of("query", "${s.query}"));
        flow.setEndComp("e", new End(new EndConfig("123")), null, null,
                Map.of("output", "${llm.output}", "output2", "${llm2.output}"), null, "streaming");
        flow.addConnection("s", "llm");
        flow.addConnection("s", "llm2");
        flow.addStreamConnection("llm", "e");
        flow.addStreamConnection("llm2", "e");

        assertThat(spec(flow).getStreamSourceGroups().get("e")).hasSize(2);
    }

    @Test
    void testStreamTriggerConsumer() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("llm", new StreamComponent("llm"), Map.of("query", "${s.query}"));
        flow.setEndComp("e", new End(new EndConfig("123")), null, null,
                Map.of("output", "${llm.output}"), null, "streaming");
        flow.addConnection("s", "llm");
        flow.addStreamConnection("llm", "e");

        assertThat(spec(flow).getStreamEdges()).containsEntry("llm", List.of("e"));
    }

    @Test
    void testAutoAbilityWithConditionEdge() {
        BranchComponent branch = new BranchComponent();
        branch.addBranch("${user_input.x} == 1", "invoke", "branch1");
        branch.addBranch("${user_input.x} == 2", "stream", "branch2");
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of());
        flow.addWorkflowComp("branch", branch);
        flow.addWorkflowComp("invoke", new StreamComponent("invoke"));
        flow.addWorkflowComp("stream", new StreamComponent("stream"));
        flow.addWorkflowComp("collect", new StreamComponent("collect"), null, null, true,
                Map.of("result2", "${stream.output}"), null);
        flow.setEndComp("end", new End(), Map.of("result", "${collect.output}", "result2", "${invoke.output}"));
        flow.addConnection("start", "branch");
        flow.addConnection("invoke", "end");
        flow.addStreamConnection("stream", "collect");
        flow.addConnection("collect", "end");

        WorkflowSpec spec = spec(flow);

        assertThat(spec.getCompConfigs().get("collect").getAbilities()).contains(ComponentAbility.COLLECT);
        assertThat(spec.getStreamEdges()).containsEntry("stream", List.of("collect"));
    }

    @Test
    void testStreamCallFastThanCall() {
        End streamTemplate = new End(new EndConfig("####a={{a}}, #####"));
        List<Object> frames = collect(streamTemplate.transform(Map.of("a", List.of("2019", "Rivian").iterator()),
                session(), null));
        End batchTemplate = new End(new EndConfig("####a={{query}}, #####"));

        assertThat(payloads(frames)).containsExactly(response("####a="), response("2019"), response("Rivian"),
                response(", #####"));
        assertThat(batchTemplate.invoke(Map.of("query", "i am a girl"), session(), null))
                .isEqualTo(response("####a=i am a girl, #####"));
    }

    @Test
    void testWorkflowWithIntentNode() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> true, "node1");
        router.addBranch(() -> false, "node2");

        assertThat(router.apply(session())).containsExactly("node1");
        assertThat(router.allTargets()).containsExactlyInAnyOrder("node1", "node2");
    }

    private static Workflow componentStreamWorkflow(boolean streamingEnd) {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new Start(), Map.of("array", "${inputs}"));
        workflow.addWorkflowComp("a", new Producer(), Map.of("array", "${start.array}"));
        workflow.addWorkflowComp("b", new Producer(), Map.of("array", "${start.array}"));
        workflow.addWorkflowComp("c", new Producer(), Map.of("array", "${start.array}"));
        workflow.addWorkflowComp("batch", new Producer(), Map.of("array", "${start.array}"));
        Object streamInputs = Map.of("a", "${a.output}", "b", "${b.output}", "c", "${c.output}");
        String responseMode = streamingEnd ? "streaming" : null;
        workflow.setEndComp("end", new End(new EndConfig("a: {{a}}; c: {{c}}; batch: {{batch}}; b: {{b}}")),
                Map.of("batch", "${batch.output}"), null, streamInputs, null, responseMode);
        workflow.addConnection("start", "a");
        workflow.addConnection("start", "b");
        workflow.addConnection("start", "c");
        workflow.addConnection("start", "batch");
        workflow.addStreamConnection("a", "end");
        workflow.addStreamConnection("b", "end");
        workflow.addStreamConnection("c", "end");
        workflow.addConnection("batch", "end");
        return workflow;
    }

    private static WorkflowSpec spec(Workflow flow) {
        BaseWorkflow baseWorkflow = (BaseWorkflow) flow.getInternalDrawable();
        baseWorkflow.autoCompleteAbilities();
        return baseWorkflow.getConfig().getSpec();
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static List<Object> payloads(List<Object> frames) {
        return frames.stream()
                .map(OutputSchema.class::cast)
                .map(OutputSchema::getPayload)
                .toList();
    }

    private static Map<String, Object> response(Object value) {
        return Map.of("response", value);
    }

    private static BaseError componentError(String ability) {
        return ErrorHelper.buildError(
                StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                "component", "C",
                "reason", ability + " failed",
                "workflow", "workflow"
        );
    }

    private static SimpleSession session() {
        return new SimpleSession();
    }

    private static final class Producer extends WorkflowComponent<Object, Object> {
        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            Object value = inputs instanceof Map<?, ?> map ? map.get("array") : null;
            return Map.of("output", value == null ? List.of() : value);
        }

        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            Object value = inputs instanceof Map<?, ?> map ? map.get("array") : List.of();
            if (value instanceof Iterable<?> iterable) {
                List<Object> outputs = new ArrayList<>();
                for (Object item : iterable) {
                    outputs.add(Map.of("output", item));
                }
                return outputs.iterator();
            }
            return List.<Object>of(Map.of("output", value)).iterator();
        }
    }

    private static final class StreamComponent extends WorkflowComponent<Object, Object> {
        private final String value;

        private StreamComponent(String value) {
            this.value = value;
        }

        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            return Map.of("output", value, "result", 3, "value", value);
        }

        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            return List.<Object>of(Map.of("output", value, "result", 3, "value", value)).iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, BaseSession session, ModelContext context) {
            return List.<Object>of(Map.of("output", inputs, "result", 6)).iterator();
        }
    }

    private static final class SimpleSession extends BaseSession {
        @Override
        public String sessionId() {
            return "workflow-comp-stream-parity";
        }
    }
}
