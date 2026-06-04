/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;
import com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow interrupt and checkpoint tests.
 *
 * <p>Mirrors Python's {@code test_workflow_with_interrupt.py} in
 * {@code tests/unit_tests/core/workflow/test_workflow_with_interrupt.py}.</p>
 */
@DisplayName("TestWorkflowWithInterrupt")
class TestWorkflowWithInterrupt {

    @AfterEach
    void cleanupCheckpointer() {
        CheckpointerFactory.getCheckpointer().release("test-session");
    }

    @Test
    void testSimpleWorkflow() {
        RuntimeCountingStart start = new RuntimeCountingStart();
        ThresholdNode node = new ThresholdNode(20);
        Workflow flow = createFailingWorkflow("simple_workflow", start, node);

        BaseError error = assertThrows(BaseError.class,
                () -> flow.invoke(Map.of("inputs", Map.of("a", 1, "b", "haha")),
                        newSession("simple-1"), null));

        assertEquals(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("value < 20"));
        assertEquals(1, start.runtime);
        assertEquals(1, node.runtime);
    }

    @Test
    void testWorkflowComp() {
        RuntimeCountingStart subStart = new RuntimeCountingStart();
        ThresholdNode subNode = new ThresholdNode(20);
        Workflow subFlow = createFailingWorkflow("test_workflow_comp", subStart, subNode);
        Workflow flow = new Workflow(new WorkflowCard("outer_workflow", "outer_workflow"));
        flow.setStartComp("start", new Start(), Map.of("a", "${inputs.a}", "b", "${inputs.b}"), null);
        flow.addWorkflowComp("a", new SubWorkflowComponentImpl(subFlow),
                Map.of("inputs", Map.of("a", "${start.a}", "b", "${start.b}")), null);
        flow.setEndComp("end", new End(), Map.of("result", "${a.result}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("a", "end");

        BaseError error = assertThrows(BaseError.class,
                () -> flow.invoke(Map.of("inputs", Map.of("a", 1, "b", "haha")),
                        newSession("comp-1"), null));

        assertEquals(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("value < 20"));
        assertEquals(1, subStart.runtime);
        assertEquals(1, subNode.runtime);
    }

    @Test
    void testWorkflowWithLoop() {
        AddTenNode4Cp addTen = new AddTenNode4Cp();
        NodeSessionApi session = WorkflowTestSupport.nodeSession("2");
        session.updateGlobalState(Map.of("a", 1));

        RuntimeException first = assertThrows(RuntimeException.class,
                () -> addTen.invoke(Map.of("source", 1), session, null));
        assertTrue(first.getMessage().contains("inner error: 1"));

        session.updateGlobalState(Map.of("a", 21));
        assertEquals(Map.of("result", 11), addTen.invoke(Map.of("source", 1), session, null));
    }

    @Test
    void testWorkflowWithLoopInteractive() {
        WorkflowOutput completed = completeTwoStepInteractiveWorkflow(
                createSimpleInteractiveWorkflow("test_workflow_with_loop_interactive"), Map.of("aa", "any key"));

        assertEquals(WorkflowExecutionState.COMPLETED, completed.getState());
        assertEquals(Map.of("output", Map.of("result", "any key")), completed.getResult());
    }

    @Test
    void testWorkflowWithLoopCompInteractive() {
        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp("ask", new InteractiveNode(), Map.of("aa", "${loop.item}"));
        loopGroup.addWorkflowComp("set", new LoopSetVariableComponent(Map.of("${loop.user_var}", "${ask.aa}")));
        loopGroup.startNodes(List.of("ask"));
        loopGroup.endNodes(List.of("set"));
        loopGroup.addConnection("ask", "set");

        LoopComponentImpl loop = new LoopComponentImpl(loopGroup, Map.of("result", "${ask.aa}"));
        Workflow flow = new Workflow(new WorkflowCard("loop_comp_interactive", "loop_comp_interactive"));
        flow.setStartComp("start", new Start(), Map.of("items", "${items}"), null);
        flow.addWorkflowComp("loop", loop, Map.of("loop_type", "array",
                "loop_array", Map.of("item", "${start.items}")));
        flow.setEndComp("end", new End(), Map.of("result", "${loop.result}"), null);
        flow.addConnection("start", "loop");
        flow.addConnection("loop", "end");

        WorkflowOutput first = flow.invoke(Map.of("items", List.of("x")), newSession("loop-comp"), null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, first.getState());
        assertInteraction(first, "loop.ask", 0);
    }

    @Test
    void testSimpleInteractiveWorkflow() {
        WorkflowOutput completed = completeTwoStepInteractiveWorkflow(
                createSimpleInteractiveWorkflow("test_simple_interactive_workflow"), Map.of("aa", "any key"));

        assertEquals(Map.of("output", Map.of("result", "any key")), completed.getResult());
    }

    @Test
    void testSimpleStreamInteractiveWorkflow() {
        Workflow flow = createStreamInteractiveWorkflow("test_simple_stream_interactive_workflow");
        WorkflowOutput first = flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession("stream-interactive"), null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, first.getState());
        assertInteraction(first, "a", 0);
    }

    @Test
    void testCollectNodeInteractiveWorkflow() {
        Workflow flow = new Workflow(new WorkflowCard("test_collect_node_interactive_workflow",
                "test_collect_node_interactive_workflow"));
        flow.setStartComp("start", new Start(), Map.of("a", "${inputs.a}"), null);
        flow.addWorkflowComp("producer", new StreamProducer(), true, Map.of("a", "${start.a}"),
                null, null, null, List.of(ComponentAbility.STREAM));
        flow.addWorkflowComp("collect", new CollectInteractiveNode(), true, null, null,
                Map.of("value", "${producer.value}"), null, List.of(ComponentAbility.COLLECT));
        flow.setEndComp("end", new End(), Map.of("result", "${collect}"), null);
        flow.addConnection("start", "producer");
        flow.addStreamConnection("producer", "collect");
        flow.addConnection("collect", "end");

        BaseError error = assertThrows(BaseError.class, () -> flow.invoke(Map.of("inputs", Map.of("a", 1)),
                newSession("collect-interactive"), null));

        assertEquals(StatusCode.COMP_SESSION_INTERACT_ERROR.getCode(), error.getCode());
    }

    @Test
    void testSimpleConcurrentInteractiveWorkflow() {
        Workflow flow = new Workflow(new WorkflowCard("test_simple_concurrent_interactive_workflow",
                "test_simple_concurrent_interactive_workflow"));
        flow.setStartComp("start", new Start(), Map.of("a", "${inputs.a}"), null);
        flow.addWorkflowComp("a", new InteractiveNode(), Map.of("aa", "${start.a}"), null);
        flow.addWorkflowComp("b", new InteractiveNode(), Map.of("aa", "${start.a}"), null);
        flow.setEndComp("end", new End(), Map.of("a", "${a.aa}", "b", "${b.aa}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("start", "b");
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        WorkflowOutput first = flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession("concurrent"), null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, first.getState());
        assertFalse(((List<?>) first.getResult()).isEmpty());
    }

    @Test
    void testWorkflowWithBranch() {
        Workflow flow = new Workflow(new WorkflowCard("test_workflow_with_branch", "test_workflow_with_branch"));
        flow.setStartComp("start", new Start(), null, null);
        flow.setEndComp("end", new End(), Map.of("a", "${a.result}", "b", "${b.result}"), null);
        BranchComponent branch = new BranchComponent();
        branch.addBranch("${a} <= 10", List.of("b"), "1");
        branch.addBranch("${a} > 10", List.of("a"), "2");
        flow.addWorkflowComp("sw", branch);
        flow.addWorkflowComp("a", new IdentityNode(), Map.of("result", "${a}"), null);
        flow.addWorkflowComp("b", new AddTenNode(), Map.of("source", "${a}"), null);
        flow.addConnection("start", "sw");
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        List<OutputSchema> small = collectOutputChunks(flow, Map.of("a", 2));
        List<OutputSchema> big = collectOutputChunks(flow, Map.of("a", 15));

        assertTrue(small.stream().anyMatch(chunk -> chunk.getPayload().toString().contains("b=12")));
        assertTrue(big.stream().anyMatch(chunk -> chunk.getPayload().toString().contains("a=15")));
    }

    @Test
    void testSimpleInteractiveWorkflowRawInput() {
        Workflow flow = createSimpleInteractiveWorkflow("test_simple_interactive_workflow_raw_input");
        String sessionId = "raw-input";
        WorkflowOutput first = flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);
        assertInteraction(first, "a", 0);

        WorkflowOutput second = flow.invoke(new InteractiveInput(Map.of("aa", "any key")),
                newSession(sessionId), null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, second.getState());
        assertInteraction(second, "a", 1);
    }

    @Test
    void testSimpleInteractiveWorkflowBothRawInputUpdate() {
        InteractiveInput raw = new InteractiveInput(Map.of("aa", "any key"));

        BaseError error = assertThrows(BaseError.class,
                () -> raw.update("a", Map.of("aa", "abc")));

        assertEquals(StatusCode.INTERACTION_INPUT_INVALID.getCode(), error.getCode());
    }

    @Test
    void testSimpleInteractiveWorkflowRawInputsEmptyStrList() {
        for (Object rawInputs : List.of(List.of(), "")) {
            Workflow flow = createSimpleInteractiveWorkflow("raw_inputs_" + rawInputs.hashCode());
            String sessionId = "raw-empty-" + rawInputs.hashCode();
            flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);

            WorkflowOutput result = flow.invoke(new InteractiveInput(rawInputs), newSession(sessionId), null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, result.getState());
            assertInteraction(result, "a", 1);
        }
    }

    @Test
    void testSimpleInteractiveWorkflowUpdateEmptyStrList() {
        for (Object rawInputs : List.of(List.of(), "")) {
            Workflow flow = createSimpleInteractiveWorkflow("update_empty_" + rawInputs.hashCode());
            String sessionId = "update-empty-" + rawInputs.hashCode();
            flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);
            InteractiveInput input = new InteractiveInput();
            input.update("a", rawInputs);

            WorkflowOutput result = flow.invoke(input, newSession(sessionId), null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, result.getState());
            assertInteraction(result, "a", 1);
        }
    }

    @Test
    void testSimpleInteractiveWorkflowNone() {
        Workflow flow = createSimpleInteractiveWorkflow("test_simple_interactive_workflow_none");
        String sessionId = "none-input";
        flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);

        WorkflowOutput result = flow.invoke(new InteractiveInput(), newSession(sessionId), null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, result.getState());
        assertInteraction(result, "a", 0);
    }

    @Test
    void testSimpleInteractiveWorkflowCheckpointer() {
        String sessionId = "checkpointer";
        String workflowId = "test_simple_interactive_workflow_checkpointer";
        Workflow flow = createSimpleInteractiveWorkflow(workflowId);

        WorkflowOutput first = flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, first.getState());
        assertTrue(CheckpointerFactory.getCheckpointer().sessionExists(sessionId));

        WorkflowOutput completed = continueWithInput(flow, sessionId, "a", Map.of("aa", "any key"));
        completed = continueWithInput(flow, sessionId, "a", Map.of("aa", "any key"));

        assertEquals(WorkflowExecutionState.COMPLETED, completed.getState());
        assertFalse(CheckpointerFactory.getCheckpointer().sessionExists(sessionId));
    }

    @Test
    void testSimpleInteractiveWorkflowCheckpointerManualRelease() {
        String sessionId = "manual-release";
        String workflowId = "test_simple_interactive_workflow_checkpointer";
        Workflow flow = createSimpleInteractiveWorkflow(workflowId);
        flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();

        assertTrue(checkpointer.sessionExists(sessionId));
        checkpointer.release(sessionId);

        assertFalse(checkpointer.sessionExists(sessionId));
        assertTrue(checkpointer.graphStore().get(sessionId, workflowId).isEmpty());
    }

    @Test
    void testSimpleInteractiveWorkflowClearCheckpointer() {
        String sessionId = "clear-checkpointer";
        Workflow flow = createSimpleInteractiveWorkflow("test_simple_interactive_workflow");
        flow.invoke(Map.of("inputs", Map.of("a", 1)), newSession(sessionId), null);
        WorkflowSessionApi forceSession = new WorkflowSessionApi(null, sessionId,
                Map.of(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, true));

        WorkflowOutput restarted = flow.invoke(Map.of("inputs", Map.of("a", 1)), forceSession, null);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, restarted.getState());
        assertInteraction(restarted, "a", 0);
    }

    private static Workflow createFailingWorkflow(String workflowId, RuntimeCountingStart start, ThresholdNode node) {
        Workflow flow = new Workflow(new WorkflowCard(workflowId, workflowId));
        flow.setStartComp("start", start, Map.of("a", "${inputs.a}", "b", "${inputs.b}"), null);
        flow.addWorkflowComp("a", node, Map.of("aa", "${start.a}"), null);
        flow.setEndComp("end", new End(), Map.of("result", "${a.aa}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("a", "end");
        return flow;
    }

    private static Workflow createSimpleInteractiveWorkflow(String workflowId) {
        Workflow flow = new Workflow(new WorkflowCard(workflowId, workflowId));
        flow.setStartComp("start", new Start(), Map.of("a", "${inputs.a}", "b", "${inputs.b}"), null);
        flow.addWorkflowComp("a", new InteractiveNode(), Map.of("aa", "${start.a}"), null);
        flow.setEndComp("end", new End(), Map.of("result", "${a.aa}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("a", "end");
        return flow;
    }

    private static Workflow createStreamInteractiveWorkflow(String workflowId) {
        Workflow flow = new Workflow(new WorkflowCard(workflowId, workflowId));
        flow.setStartComp("start", new Start(), Map.of("a", "${inputs.a}"), null);
        flow.addWorkflowComp("a", new StreamInteractiveNode(), true, Map.of("aa", "${start.a}"),
                null, null, null, List.of(ComponentAbility.STREAM));
        flow.setEndComp("end", new End(), null, null, Map.of("result", "${a}"), null, "streaming");
        flow.addConnection("start", "a");
        flow.addStreamConnection("a", "end");
        return flow;
    }

    private static WorkflowOutput completeTwoStepInteractiveWorkflow(Workflow flow, Object answer) {
        String sessionId = UUID.randomUUID().toString();
        WorkflowOutput first = flow.invoke(Map.of("inputs", Map.of("a", 1, "b", "haha")),
                newSession(sessionId), null);
        assertInteraction(first, "a", 0);
        WorkflowOutput second = continueWithInput(flow, sessionId, "a", answer);
        assertInteraction(second, "a", 1);
        return continueWithInput(flow, sessionId, "a", answer);
    }

    private static WorkflowOutput continueWithInput(Workflow flow, String sessionId, String nodeId, Object value) {
        InteractiveInput input = new InteractiveInput();
        input.update(nodeId, value);
        return flow.invoke(input, newSession(sessionId), null);
    }

    private static void assertInteraction(WorkflowOutput output, String id, int index) {
        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, output.getState());
        assertInstanceOf(List.class, output.getResult());
        List<?> outputs = (List<?>) output.getResult();
        assertFalse(outputs.isEmpty());
        assertInstanceOf(OutputSchema.class, outputs.get(0));
        OutputSchema schema = (OutputSchema) outputs.get(0);
        assertEquals(Constant.INTERACTION, schema.getType());
        assertEquals(index, schema.getIndex());
        assertInstanceOf(InteractionOutput.class, schema.getPayload());
        InteractionOutput payload = (InteractionOutput) schema.getPayload();
        assertEquals(id, payload.getId());
        assertEquals("Please enter any key", payload.getValue());
    }

    private static List<OutputSchema> collectOutputChunks(Workflow workflow, Map<String, Object> inputs) {
        Iterator<WorkflowChunk> iterator = workflow.stream(inputs, newSession(UUID.randomUUID().toString()),
                null, List.of(StreamMode.OUTPUT));
        List<OutputSchema> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            WorkflowChunk chunk = iterator.next();
            assertInstanceOf(OutputSchema.class, chunk);
            chunks.add((OutputSchema) chunk);
        }
        return chunks;
    }

    private static WorkflowSessionApi newSession(String sessionId) {
        return new WorkflowSessionApi(null, sessionId, Map.of());
    }

    private static final class RuntimeCountingStart extends Start {
        private int runtime;

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            runtime++;
            session.updateGlobalState(Map.of("a", 10));
            return inputs;
        }
    }

    private static final class ThresholdNode extends WorkflowComponent {
        private final int threshold;
        private int runtime;

        private ThresholdNode(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (((Number) value).intValue() < threshold) {
                throw new RuntimeException("value < " + threshold);
            }
            return inputs;
        }
    }

    private static final class AddTenNode4Cp extends WorkflowComponent {
        private boolean raiseException = true;

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            int source = ((Number) ((Map<?, ?>) inputs).get("source")).intValue();
            if (raiseException) {
                raiseException = false;
                throw new RuntimeException("inner error: " + source);
            }
            raiseException = true;
            return Map.of("result", source + 10);
        }
    }

    private static class InteractiveNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            session.interact("Please enter any key");
            return session.interact("Please enter any key");
        }
    }

    private static final class StreamInteractiveNode extends WorkflowComponent {
        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = session.interact("Please enter any key");
            session.writeStream(new OutputSchema("output", 0, Map.of("a", result)));
            return List.<Object>of(Map.of("a", result)).iterator();
        }
    }

    private static final class CollectInteractiveNode extends WorkflowComponent {
        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            session.interact("Please enter any key");
            return Map.of("result", 3);
        }
    }

    private static final class StreamProducer extends WorkflowComponent {
        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            return List.<Object>of(Map.of("value", 1), Map.of("value", 2)).iterator();
        }
    }

    private static final class IdentityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static final class AddTenNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            int source = ((Number) ((Map<?, ?>) inputs).get("source")).intValue();
            return Map.of("result", source + 10);
        }
    }

    private static final class WorkflowTestSupport {
        private WorkflowTestSupport() {
        }

        private static NodeSessionApi nodeSession(String nodeId) {
            return new NodeSessionApi(new com.openjiuwen.core.session.internal.NodeSession(
                    new com.openjiuwen.core.session.internal.WorkflowSession("interrupt_test"), nodeId));
        }
    }
}
