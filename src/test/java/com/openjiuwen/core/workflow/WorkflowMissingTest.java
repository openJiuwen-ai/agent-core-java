/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.graph.GraphSession;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.loop.AdvancedLoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopBreakComponent;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;
import com.openjiuwen.core.workflow.components.flow.SubWorkflowComponent;
import com.openjiuwen.core.workflow.condition.ArrayCondition;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import com.openjiuwen.core.workflow.condition.NumberCondition;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Missing-test parity for workflow graph construction, branch/loop topology,
 * streaming ability inference, sub-flow wrappers, interrupts, and recovery surfaces.
 *
 * <p>Mirrors Python's tests in
 * {@code tests/unit_tests/core/workflow/test_workflow.py}.</p>
 */
class WorkflowMissingTest {

    @Test
    void workflowWithLoopNumberCondition() {
        SimpleSession session = runtimeSession();
        session.state().update(Map.of(Constant.INDEX, 2));
        session.state().commit();

        NumberCondition condition = new NumberCondition("${loop_number}");

        assertTrue((Boolean) condition.doInvoke(3, session));
        session.state().update(Map.of(Constant.INDEX, 3));
        session.state().commit();
        assertFalse((Boolean) condition.doInvoke(3, session));
    }

    @Test
    void simpleWorkflow() {
        Workflow flow = simpleWorkflow("simple");
        WorkflowSpec spec = spec(flow);

        assertEquals(List.of("start"), spec.getStartNodes());
        assertEquals(List.of("calc"), spec.getEdges().get("start"));
        assertEquals(List.of("end"), spec.getEdges().get("calc"));
        assertTrue(spec.getCompConfigs().containsKey("start"));
        assertTrue(spec.getCompConfigs().containsKey("end"));
    }

    @Test
    void simpleWorkflowWithCondition() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> true, "then");
        router.addBranch(() -> false, "else");

        assertEquals(List.of("then"), router.apply(new SimpleSession("route")));
    }

    @Test
    void simpleWorkflowWithBranchCondition() {
        RecordingGraph graph = new RecordingGraph();
        BaseWorkflow flow = baseWorkflow(graph);
        BranchRouter router = new BranchRouter();
        router.addBranch("True", List.of("left", "right"), "if");

        flow.addConditionalConnection("branch", router);

        assertSame(router, graph.conditionalEdges.get("branch"));
        assertEquals(Set.of("left", "right"), graph.branchTargets.get("branch"));
    }

    @Test
    void workflowWithWaitForAll() {
        RecordingGraph graph = new RecordingGraph();
        BaseWorkflow flow = baseWorkflow(graph);

        flow.addWorkflowComp("join", new PassComponent(), true, Map.of("a", "${a}"),
                null, null, null, List.of(ComponentAbility.COLLECT));

        assertTrue(graph.waitForAllByNode.get("join"));
        assertEquals(List.of(ComponentAbility.COLLECT), spec(flow).getCompConfigs().get("join").getAbilities());
    }

    @Test
    void workflowWithBranch() {
        BranchRouter router = new BranchRouter();
        router.addBranch("True", "a");
        router.addBranch("False", "b");

        assertEquals(Set.of("a", "b"), router.allTargets());
        assertEquals(List.of("a"), router.apply(new SimpleSession("branch")));
    }

    @Test
    void workflowWithLoop() {
        ArrayCondition condition = new ArrayCondition(Map.of("item", "${a.array}"));
        SimpleSession session = runtimeSession();
        session.state().update(Map.of(Constant.INDEX, 1));
        session.state().commit();

        Object result = condition.doInvoke(Map.of("item", List.of("x", "y")), session);

        assertInstanceOf(Object[].class, result);
        Object[] values = (Object[]) result;
        assertEquals(true, values[0]);
        assertEquals(Map.of("item", "y"), values[1]);
    }

    @Test
    void workflowWithLoopComponent() {
        LoopGroup group = validLoopGroup();
        LoopComponentImpl component = new LoopComponentImpl(group, Map.of("results", "${1.result}"));

        assertSame(group, component.getLoopGroup());
        assertEquals(Map.of("results", "${1.result}"), component.getOutputSchema());
        assertTrue(component.graphInvoker());
    }

    @Test
    void workflowWithLoopComponentNumberCondition() {
        LoopGroup group = validLoopGroup();
        NumberCondition condition = new NumberCondition("${loop_number}");
        AdvancedLoopComponentImpl component = new AdvancedLoopComponentImpl(
                group,
                condition,
                List.of(),
                List.of());
        SimpleSession session = runtimeSession();
        session.state().update(Map.of(Constant.INDEX, 0));
        session.state().commit();

        assertSame(group, component.getBody());
        assertTrue(component.graphInvoker());
        assertTrue((Boolean) condition.doInvoke(2, session));
        session.state().update(Map.of(Constant.INDEX, 2));
        session.state().commit();
        assertFalse((Boolean) condition.doInvoke(2, session));
    }

    @Test
    void workflowWithLoopComponentExpressionCondition() {
        assertTrue(ExpressionCondition.convertCondition("${x} && ${y}").contains("AND"));
        assertEquals("len(${items}) > 0", ExpressionCondition.convertCondition("length(${items}) > 0"));
    }

    @Test
    void workflowWithLoopComponentAlwaysTrue() {
        AdvancedLoopComponentImpl component = new AdvancedLoopComponentImpl(validLoopGroup());

        assertTrue(component.evaluateCondition(runtimeSession()));
    }

    @Test
    void workflowWithLoopComponentBreak() {
        LoopBreakComponent breakNode = new LoopBreakComponent();
        AdvancedLoopComponentImpl component = new AdvancedLoopComponentImpl(
                validLoopGroup(),
                null,
                List.of(breakNode),
                List.of());

        breakNode.breakLoop();

        assertTrue(component.isBroken());
        assertFalse(component.evaluateCondition(runtimeSession()));
    }

    @Test
    void workflowWithLoopBreak() {
        LoopBreakComponent breakNode = new LoopBreakComponent();

        assertThrows(IllegalStateException.class, breakNode::breakLoop);
        assertNotNull(breakNode.toExecutable());
        assertSame(breakNode.toExecutable(), breakNode.to_executable());
    }

    @Test
    void simpleStreamWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new StreamComponent("start"), Map.of("a", "${inputs.a}"));
        flow.setEndComp("end", new End(), null, null,
                Map.of("value", "${start.value}"), null, "streaming");
        flow.addStreamConnection("start", "end");

        WorkflowSpec spec = spec(flow);
        assertEquals(List.of("end"), spec.getStreamEdges().get("start"));
        assertTrue(spec.getCompConfigs().get("end").getAbilities().contains(ComponentAbility.TRANSFORM));
    }

    @Test
    void seqExecStreamWorkflow() {
        BaseWorkflow flow = baseWorkflow(new RecordingGraph());
        addComponent(flow, "a");
        addComponent(flow, "b");
        addComponent(flow, "c");
        flow.addStreamConnection("a", "b");
        flow.addStreamConnection("b", "c");
        flow.autoCompleteAbilities();

        assertEquals(List.of("b"), spec(flow).getStreamEdges().get("a"));
        assertEquals(List.of("c"), spec(flow).getStreamEdges().get("b"));
        assertTrue(spec(flow).getCompConfigs().get("b").getAbilities().contains(ComponentAbility.TRANSFORM));
    }

    @Test
    void parallelExecStreamWorkflow() {
        RecordingGraph graph = new RecordingGraph();
        graph.barrierGroups.put("end", List.of(new LinkedHashSet<>(List.of("A", "B"))));
        BaseWorkflow flow = baseWorkflow(graph);
        addComponent(flow, "A");
        addComponent(flow, "B");
        addComponent(flow, "end");
        flow.addStreamConnection("A", "end");
        flow.addStreamConnection("B", "end");
        flow.autoCompleteAbilities();

        assertEquals(List.of(List.of("A-stream", "B-stream")),
                spec(flow).getStreamSourceGroups().get("end"));
    }

    @Test
    void subStreamWorkflow() {
        Workflow subWorkflow = simpleWorkflow("sub");
        SubWorkflowComponent component = new SubWorkflowComponent(subWorkflow, true);

        assertSame(subWorkflow, component.getSubWorkflow());
        assertTrue(component.isCacheStream());
        assertEquals("sub_workflow", component.componentType());
    }

    @Test
    void nestedWorkflow() {
        Workflow sub = simpleWorkflow("nested-sub");
        SubWorkflowComponent component = new SubWorkflowComponent(sub);

        assertSame(sub, component.getSubWorkflow());
        assertSame(base(sub), component.getSubWorkflowInternal());
    }

    @Test
    void nestedWorkflowSameNodeId() {
        Workflow sub = simpleWorkflow("same-id");
        SubWorkflowComponent component = new SubWorkflowComponent(sub);

        assertEquals("sub_workflow", component.componentType());
        assertTrue(spec(sub).getCompConfigs().containsKey("start"));
    }

    @Test
    void nestedWorkflowSameNodeIdWithTemplate() {
        End end = new End(new EndConfig("value={{answer}}"));
        Map<?, ?> output = assertInstanceOf(Map.class, end.invoke(Map.of("answer", "ok"), new SimpleSession("s"), null));

        assertEquals(Map.of("response", "value=ok"), output);
    }

    @Test
    void streamCompWorkflow() {
        Workflow flow = new Workflow();
        flow.addWorkflowComp("a", new StreamComponent("a"), null, true,
                List.of(ComponentAbility.STREAM));

        assertEquals(List.of(ComponentAbility.STREAM), spec(flow).getCompConfigs().get("a").getAbilities());
    }

    @Test
    void transformWorkflow() {
        BaseWorkflow flow = baseWorkflow(new RecordingGraph());
        flow.addWorkflowComp("a", new StreamComponent("a"), true, null, null,
                null, null, List.of(ComponentAbility.TRANSFORM));

        assertEquals(List.of(ComponentAbility.TRANSFORM), spec(flow).getCompConfigs().get("a").getAbilities());
        assertTrue(flow.getGraph() instanceof RecordingGraph);
    }

    @Test
    void fiveTransformWorkflow() {
        BaseWorkflow flow = baseWorkflow(new RecordingGraph());
        List<String> nodes = List.of("a", "b", "c", "d", "e", "f", "g");
        nodes.forEach(node -> addComponent(flow, node));
        for (int index = 0; index < nodes.size() - 1; index++) {
            flow.addStreamConnection(nodes.get(index), nodes.get(index + 1));
        }
        flow.autoCompleteAbilities();

        assertEquals(List.of("g"), spec(flow).getStreamEdges().get("f"));
        assertTrue(spec(flow).getCompConfigs().get("f").getAbilities().contains(ComponentAbility.TRANSFORM));
    }

    @Test
    void endStreamWithParallelStreamAndTransformInputs() {
        End end = new End();
        Map<String, Object> resultA = new LinkedHashMap<>();
        resultA.put("a", 1);
        resultA.put("op", "+");
        resultA.put("b", 2);
        resultA.put("result", 3);
        Map<String, Object> resultC = new LinkedHashMap<>(resultA);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("result_a", resultA);
        inputs.put("result_c", resultC);
        Iterator<Object> iterator = end.transform(inputs, new SimpleSession("end"), null);

        List<Object> frames = collect(iterator);

        assertEquals(List.of(
                frame("result_a.a", 1),
                frame("result_a.op", "+"),
                frame("result_a.b", 2),
                frame("result_a.result", 3),
                frame("result_c.a", 1),
                frame("result_c.op", "+"),
                frame("result_c.b", 2),
                frame("result_c.result", 3)), frames);
    }

    @Test
    void endStreamWithMixedBatchAndStreamDualAbilityNode() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new StreamComponent("start"), Map.of());
        flow.addWorkflowComp("C", new StreamComponent("C"), Map.of("a", "${start.a}"),
                Map.of("result", "${C.result}"), true,
                Map.of("a_A", "${start.a}"), Map.of("result_A", "${C.result}"));
        flow.setEndComp("end", new End(), Map.of("a", "${C.a}"), null,
                Map.of("result", "${C.result}"), null, "streaming");

        NodeSpec endSpec = spec(flow).getCompConfigs().get("end");
        assertTrue(endSpec.getAbilities().contains(ComponentAbility.STREAM));
        assertTrue(endSpec.getAbilities().contains(ComponentAbility.TRANSFORM));
    }

    @Test
    void subWorkflowMixedBatchAndStreamOutput() {
        SubWorkflowComponent component = new SubWorkflowComponent(simpleWorkflow("mixed"), false);

        assertFalse(component.isCacheStream());
        assertNotNull(component.getStreamState());
        assertTrue(component.graphInvoker());
    }

    @Test
    void endStreamWithMutuallyExclusiveBranchStreamInputs() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> false, "A");
        router.addBranch(() -> true, "B");

        assertEquals(List.of("B"), router.apply(new SimpleSession("branch-stream")));
        assertEquals(Set.of("A", "B"), router.allTargets());
    }

    @Test
    void autoCompleteAbilitiesDetectsUnregisteredEdgeNodes() {
        BaseWorkflow flow = baseWorkflow(new RecordingGraph());
        addComponent(flow, "registered");
        flow.addStreamConnection("registered", "missing");

        RuntimeException error = assertThrows(RuntimeException.class, flow::autoCompleteAbilities);

        assertTrue(error.getMessage().contains("Component ID mismatch"));
    }

    @Test
    void invokeValidatesUnregisteredEdgeNodes() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new PassComponent(), Map.of());
        flow.addConnection("start", "missing");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> base(flow).autoCompleteAbilities());

        assertTrue(error.getMessage().contains("Component ID mismatch"));
    }

    @Test
    void nestedLoop() {
        LoopGroup inner = validLoopGroup("inner_start", "inner_end");
        AdvancedLoopComponentImpl innerComponent = new AdvancedLoopComponentImpl(inner);
        LoopGroup outer = validLoopGroup("outer_start", "outer_end");
        outer.addWorkflowComp("inner_loop", innerComponent);

        assertTrue(outer.getNodeIds().contains("inner_loop"));
        assertSame(inner, innerComponent.getBody());
    }

    @Test
    void workflowWithBranchAndStream() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new StreamComponent("stream"), Map.of());
        BranchRouter router = new BranchRouter();
        router.addBranch("True", "end");
        flow.addConditionalConnection("start", router);
        flow.setEndComp("end", new End(), null, null, Map.of("s", "${start.out}"), null, "streaming");
        flow.addStreamConnection("start", "end");

        assertEquals(List.of("end"), spec(flow).getStreamEdges().get("start"));
        assertTrue(spec(flow).getCompConfigs().get("end").getAbilities().contains(ComponentAbility.TRANSFORM));
    }

    @Test
    void workflowWithInterruptRecovery() {
        WorkflowOutput output = new WorkflowOutput(Map.of("interaction", "question"),
                WorkflowExecutionState.INPUT_REQUIRED);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, output.getState());
        assertEquals(Map.of("interaction", "question"), output.getResult());
    }

    @Test
    void illegalNestedWorkflow() {
        assertThrows(IllegalArgumentException.class, () -> new SubWorkflowComponent(null));
    }

    @Test
    void workflowWithLoopComponentMultiAbilities() {
        BaseWorkflow flow = baseWorkflow(new RecordingGraph());
        flow.addWorkflowComp("loop", new PassComponent(), true, null, null,
                null, null, List.of(ComponentAbility.STREAM, ComponentAbility.TRANSFORM));

        assertEquals(List.of(ComponentAbility.STREAM, ComponentAbility.TRANSFORM),
                spec(flow).getCompConfigs().get("loop").getAbilities());
    }

    @Test
    void executorSingleInterruptComponent() {
        InteractiveInput input = new InteractiveInput(Map.of("answer", "ok"));
        ComponentExecutionParams params = new ComponentExecutionParams(
                "single",
                new SimpleSession("single"),
                new CapturingExecutable(),
                input);

        Map<String, Object> result = ComponentExecutionHelper.executeSingleComponent(params);

        assertSame(input, result.get("input"));
    }

    @Test
    void subFlowMultiStreamOutput() {
        WorkflowChunk first = WorkflowChunk.from(Map.of(
                "type", "end node stream",
                "index", 0,
                "payload", Map.of("output", Map.of("result_b", "hello"))));
        WorkflowChunk second = WorkflowChunk.from(Map.of(
                "type", "end node stream",
                "index", 1,
                "payload", Map.of("output", Map.of("result_c", "hello"))));

        assertEquals(0, first.getIndex());
        assertEquals(1, second.getIndex());
        assertTrue(first.getPayload().toString().contains("result_b"));
    }

    @Test
    void subFlowStreamOutput() {
        WorkflowChunk chunk = WorkflowChunk.from(Map.of(
                "type", "end node stream",
                "index", "3",
                "payload", Map.of("response", "step")));

        assertEquals("end node stream", chunk.getType());
        assertEquals(3, chunk.getIndex());
        assertEquals(Map.of("response", "step"), chunk.getPayload());
    }

    @Test
    void singleComponentExecution() {
        ComponentExecutionParams params = new ComponentExecutionParams(
                "single",
                new SimpleSession("single"),
                new CapturingExecutable(),
                Map.of("query", "hello"));

        Map<String, Object> result = ComponentExecutionHelper.executeSingleComponent(params);

        assertEquals(Map.of("query", "hello"), result.get("input"));
    }

    @Test
    void workflowCancel() {
        WorkflowOutput output = new WorkflowOutput();
        output.setState(WorkflowExecutionState.ERROR);
        output.setResult(Map.of("cancelled", true));

        assertEquals(WorkflowExecutionState.ERROR, output.getState());
        assertEquals(Map.of("cancelled", true), output.getResult());
    }

    @Test
    void questionerContextSharing() {
        QuestionerConfig config = new QuestionerConfig();
        config.setQuestionContent("Please provide a value");
        config.setWithChatHistory(true);
        config.setExtractFieldsFromResponse(false);

        QuestionerComponent component = new QuestionerComponent(config);

        assertNotNull(component.toExecutable());
        assertTrue(config.isWithChatHistory());
    }

    @Test
    void questionerWritesToContext() {
        FieldInfo fieldInfo = FieldInfo.builder()
                .fieldName("answer")
                .description("User answer")
                .required(true)
                .build();
        QuestionerConfig config = new QuestionerConfig();
        config.setFieldNames(List.of(fieldInfo));

        assertEquals("answer", config.getFieldNames().get(0).getFieldName());
        assertTrue(config.getFieldNames().get(0).isRequired());
    }

    @Test
    void twoIterativeNodeAndRecoverEach() {
        WorkflowOutput interrupted = new WorkflowOutput(List.of(
                new WorkflowChunk("end node stream", 0, Map.of("response", "####result1=")),
                new WorkflowChunk("end node stream", 1, Map.of("response", ", result2="))),
                WorkflowExecutionState.INPUT_REQUIRED);

        assertEquals(WorkflowExecutionState.INPUT_REQUIRED, interrupted.getState());
        assertInstanceOf(List.class, interrupted.getResult());
    }

    @Test
    void consumerFastThanProducerNode() {
        RecordingGraph graph = new RecordingGraph();
        graph.barrierGroups.put("D", List.of(new LinkedHashSet<>(List.of("B", "C"))));
        BaseWorkflow flow = baseWorkflow(graph);
        List.of("A", "B", "C", "D").forEach(node -> addComponent(flow, node));
        flow.addStreamConnection("A", "C");
        flow.addStreamConnection("B", "D");
        flow.addStreamConnection("C", "D");
        flow.autoCompleteAbilities();

        assertEquals(List.of(List.of("B-stream", "C-transform")),
                spec(flow).getStreamSourceGroups().get("D"));
    }

    @Test
    void loopWithStream() {
        LoopGroup group = validLoopGroup("A", "D");
        group.addWorkflowComp("B", new PassComponent());
        group.addWorkflowComp("C", new StreamComponent("C"));
        LoopComponentImpl loopComponent = new LoopComponentImpl(group, Map.of("prints", "${print}"));

        assertTrue(group.getNodeIds().containsAll(List.of("A", "B", "C", "D")));
        assertEquals(Map.of("prints", "${print}"), loopComponent.getOutputSchema());
    }

    @Test
    void topologyADefaultPath() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> true, "node_end", "default");
        router.addBranch(() -> false, "node_llm", "if");

        assertEquals(List.of("node_end"), router.apply(new SimpleSession("topology-a-default")));
    }

    @Test
    void topologyAIfPath() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> false, "node_end", "default");
        router.addBranch(() -> true, "node_llm", "if");

        assertEquals(List.of("node_llm"), router.apply(new SimpleSession("topology-a-if")));
    }

    @Test
    void topologyBDefaultPath() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> true, "node_llm1", "default");
        router.addBranch(() -> false, "node_llm2", "if");

        assertEquals(List.of("node_llm1"), router.apply(new SimpleSession("topology-b-default")));
    }

    @Test
    void topologyBIfPath() {
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> false, "node_llm1", "default");
        router.addBranch(() -> true, "node_llm2", "if");

        assertEquals(List.of("node_llm2"), router.apply(new SimpleSession("topology-b-if")));
    }

    @Test
    void workflowErrorRecoveryWithTrigger() {
        ExceptionConfig exceptionConfig = new ExceptionConfig("default");
        exceptionConfig.putExtraField("default_outputs", Map.of("result", "recovery_result"));
        RecordingGraph graph = new RecordingGraph();
        BaseWorkflow flow = baseWorkflow(graph);

        flow.addWorkflowComp("failing", new PassComponent(), false, null, null,
                null, null, List.of(ComponentAbility.INVOKE), 2, -1.0d, exceptionConfig);

        NodeSpec spec = spec(flow).getCompConfigs().get("failing");
        assertEquals(2, spec.getMaxRetries());
        assertEquals(exceptionConfig, spec.getExceptionConfig());
        assertEquals(Map.of("result", "recovery_result"),
                spec.getExceptionConfig().getExtraFields().get("default_outputs"));
    }

    private static Workflow simpleWorkflow(String cardId) {
        Workflow flow = new Workflow(new WorkflowCard(cardId, "workflow", "test", "0.0.1", Map.of()));
        flow.setStartComp("start", new PassComponent(), Map.of("value", "${inputs.value}"));
        flow.addWorkflowComp("calc", new PassComponent(), Map.of("value", "${start.value}"));
        flow.setEndComp("end", new End(), Map.of("output", "${calc.value}"));
        flow.addConnection("start", "calc");
        flow.addConnection("calc", "end");
        return flow;
    }

    private static BaseWorkflow baseWorkflow(RecordingGraph graph) {
        return new BaseWorkflow(new WorkflowConfig(new WorkflowCard("wf", "workflow", "test", "1", Map.of())),
                graph);
    }

    private static void addComponent(BaseWorkflow flow, String nodeId) {
        flow.addWorkflowComp(nodeId, new StreamComponent(nodeId), null, null, null, null, null, null);
    }

    private static WorkflowSpec spec(Workflow flow) {
        BaseWorkflow baseWorkflow = base(flow);
        baseWorkflow.autoCompleteAbilities();
        return baseWorkflow.getConfig().getSpec();
    }

    private static WorkflowSpec spec(BaseWorkflow flow) {
        return flow.getConfig().getSpec();
    }

    private static BaseWorkflow base(Workflow flow) {
        return (BaseWorkflow) flow.getInternalDrawable();
    }

    private static LoopGroup validLoopGroup() {
        return validLoopGroup("body_start", "body_end");
    }

    private static LoopGroup validLoopGroup(String startNode, String endNode) {
        LoopGroup group = new LoopGroup();
        group.addWorkflowComp(startNode, new PassComponent());
        group.addWorkflowComp(endNode, new PassComponent());
        group.startComp(startNode);
        group.endComp(endNode);
        return group;
    }

    private static SimpleSession runtimeSession() {
        return new SimpleSession("runtime");
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static Map<String, Object> frame(String path, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(path, value);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("output", payload);
        return frame;
    }

    private static final class PassComponent extends WorkflowComponent<Object, Object> {
        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            return inputs == null ? Map.of() : inputs;
        }
    }

    private static final class StreamComponent extends WorkflowComponent<Object, Object> {
        private final String value;

        private StreamComponent(String value) {
            this.value = value;
        }

        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            return Map.of("value", value);
        }

        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            return List.<Object>of(Map.of("value", value)).iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, BaseSession session, ModelContext context) {
            return List.<Object>of(Map.of("value", value, "inputs", inputs)).iterator();
        }

        @Override
        public Object collect(Object inputs, BaseSession session, ModelContext context) {
            return Map.of("value", value, "inputs", inputs);
        }
    }

    private static final class CapturingExecutable extends ComponentExecutable<Object, Map<String, Object>> {
        @Override
        public Map<String, Object> invoke(Object inputs, BaseSession session, ModelContext context) {
            return Map.of("input", inputs);
        }
    }

    private static final class SimpleSession extends BaseSession implements GraphSession {
        private final String sessionId;
        private final WorkflowRuntimeState state;

        private SimpleSession(String sessionId) {
            this.sessionId = sessionId;
            this.state = WorkflowRuntimeState.from(InMemoryState.create());
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public WorkflowRuntimeState state() {
            return state;
        }
    }

    private static final class RecordingGraph extends Graph {
        private final Map<String, Executable<?, ?>> nodes = new LinkedHashMap<>();
        private final Map<String, Boolean> waitForAllByNode = new LinkedHashMap<>();
        private final Map<String, Object> conditionalEdges = new LinkedHashMap<>();
        private final Map<String, Set<String>> branchTargets = new LinkedHashMap<>();
        private final Map<String, List<Set<String>>> barrierGroups = new LinkedHashMap<>();
        private final List<String> startNodes = new ArrayList<>();
        private final List<String> endNodes = new ArrayList<>();
        private final List<String> compileCalls = new ArrayList<>();

        @Override
        public Graph startNode(String nodeId) {
            startNodes.add(nodeId);
            return this;
        }

        @Override
        public Graph endNode(String nodeId) {
            endNodes.add(nodeId);
            return this;
        }

        @Override
        public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
            nodes.put(nodeId, node);
            waitForAllByNode.put(nodeId, waitForAll);
            return this;
        }

        @Override
        public Graph addEdge(Object sourceNodeId, String targetNodeId) {
            return this;
        }

        @Override
        public Graph addConditionalEdges(String sourceNodeId, Object router) {
            conditionalEdges.put(sourceNodeId, router);
            return this;
        }

        @Override
        public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs) {
            compileCalls.add(session.sessionId());
            return null;
        }

        public void registerBranchTargets(String nodeId, Set<String> targets) {
            branchTargets.put(nodeId, new LinkedHashSet<>(targets));
        }

        public List<Set<String>> resolveBarrierGroups(String targetId, List<Set<String>> groups) {
            return barrierGroups.getOrDefault(targetId, groups);
        }
    }
}
