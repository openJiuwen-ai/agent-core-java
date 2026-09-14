/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class T01158WorkflowDiffTest {

    private T01158WorkflowDiffTest() {
    }

    public static void main(String[] args) {
        testAddWorkflowCompStoresRetryTimeoutAndExceptionConfig();
        testBranchRouterRegistersMultipleTargets();
        testAutoCompleteAbilitiesBuildsStreamSourceGroups();
        testExecuteSingleComponentAcceptsInteractiveInput();
        System.out.println("T01158WorkflowDiffTest passed");
    }

    private static void testAddWorkflowCompStoresRetryTimeoutAndExceptionConfig() {
        RecordingGraph graph = new RecordingGraph();
        BaseWorkflow workflow = new BaseWorkflow(null, graph);
        ExceptionConfig exceptionConfig = new ExceptionConfig("interrupt");

        workflow.addWorkflowComp("node", new NoOpComponent(), true,
                Map.of("query", "${start.query}"), Map.of("answer", "${node.answer}"),
                null, null, List.of(ComponentAbility.INVOKE), 3, 2.5d, exceptionConfig);

        NodeSpec spec = workflow.getConfig().getSpec().getCompConfigs().get("node");
        assertEquals(3, spec.getMaxRetries(), "max_retries should be stored on NodeSpec");
        assertEquals(2.5d, spec.getTimeout(), "timeout should be stored on NodeSpec");
        assertTrue(spec.getExceptionConfig() == exceptionConfig, "exception_config should be stored on NodeSpec");
        assertTrue(graph.nodes.containsKey("node"), "component should be added to graph");
        assertTrue(graph.waitForAllByNode.get("node"), "wait_for_all should be forwarded");
    }

    private static void testBranchRouterRegistersMultipleTargets() {
        RecordingGraph graph = new RecordingGraph();
        BaseWorkflow workflow = new BaseWorkflow(null, graph);
        BranchRouter router = new BranchRouter();
        router.addBranch("True", List.of("left"), "left");
        router.addBranch("False", List.of("right"), "right");

        workflow.addConditionalConnection("branch", router);

        assertTrue(graph.conditionalEdges.get("branch") == router, "router should be registered");
        assertEquals(Set.of("left", "right"), graph.branchTargets.get("branch"),
                "multi-target BranchRouter should register branch targets");
    }

    private static void testAutoCompleteAbilitiesBuildsStreamSourceGroups() {
        RecordingGraph graph = new RecordingGraph();
        BaseWorkflow workflow = new BaseWorkflow(null, graph);
        workflow.addWorkflowComp("left", new NoOpComponent(), null, null, null, null, null, null);
        workflow.addWorkflowComp("right", new NoOpComponent(), null, null, null, null, null, null);
        workflow.addWorkflowComp("sink", new NoOpComponent(), null, null, null, null, null, null);
        workflow.addStreamConnection("left", "sink");
        workflow.addStreamConnection("right", "sink");

        workflow.autoCompleteAbilities();

        Map<String, List<List<String>>> sourceGroups = workflow.getConfig().getSpec().getStreamSourceGroups();
        assertEquals(List.of(List.of("left-STREAM", "right-STREAM")), sourceGroups.get("sink"),
                "stream_source_groups should include branch-resolved stream producers");
    }

    private static void testExecuteSingleComponentAcceptsInteractiveInput() {
        InteractiveInput interactiveInput = new InteractiveInput(Map.of("answer", "ok"));
        ComponentExecutionParams params = new ComponentExecutionParams(
                "single", new SimpleSession(), new CapturingExecutable(), interactiveInput);

        Map<String, Object> result = ComponentExecutionHelper.executeSingleComponent(params);

        assertTrue(result.get("input") == interactiveInput, "InteractiveInput should be passed through");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingGraph extends Graph {
        private final Map<String, Executable<?, ?>> nodes = new LinkedHashMap<>();
        private final Map<String, Boolean> waitForAllByNode = new LinkedHashMap<>();
        private final Map<String, Object> conditionalEdges = new LinkedHashMap<>();
        private final Map<String, Set<String>> branchTargets = new LinkedHashMap<>();
        private final List<Object> streamSources = new ArrayList<>();

        @Override
        public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
            nodes.put(nodeId, node);
            waitForAllByNode.put(nodeId, waitForAll);
            return this;
        }

        @Override
        public Graph addEdge(Object sourceNodeId, String targetNodeId) {
            streamSources.add(sourceNodeId);
            return this;
        }

        @Override
        public Graph addConditionalEdges(String sourceNodeId, Object router) {
            conditionalEdges.put(sourceNodeId, router);
            return this;
        }

        public void registerBranchTargets(String nodeId, Set<String> targets) {
            branchTargets.put(nodeId, new LinkedHashSet<>(targets));
        }

        public List<Set<String>> resolveBarrierGroups(String targetId, List<Set<String>> groups) {
            if ("sink".equals(targetId)) {
                return List.of(new LinkedHashSet<>(List.of("left", "right")));
            }
            return groups;
        }
    }

    private static final class NoOpComponent implements ComponentComposable {
        @Override
        public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
            graph.addNode(nodeId, new NoOpExecutable(), waitForAll);
        }
    }

    private static final class NoOpExecutable extends ComponentExecutable<Map<String, Object>, Map<String, Object>> {
        @Override
        public Map<String, Object> invoke(Map<String, Object> inputs, BaseSession session, ModelContext context) {
            return inputs == null ? Map.of() : inputs;
        }
    }

    private static final class CapturingExecutable extends ComponentExecutable<Object, Map<String, Object>> {
        @Override
        public Map<String, Object> invoke(Object inputs, BaseSession session, ModelContext context) {
            return Map.of("input", inputs);
        }
    }

    private static final class SimpleSession extends BaseSession {
        @Override
        public String sessionId() {
            return "session-t01158";
        }
    }
}
