/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.workflow.component.BranchComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowBranchCandidateTest {

    private WorkflowBranchCandidateTest() {
    }

    public static void main(String[] args) {
        testBranchRouterRoutesFirstMatchingBranch();
        testBranchRouterAllTargetsAndExpressionHelpers();
        testBranchRouterReportsNoMatchingBranch();
        testBranchComponentValidation();
        testBranchComponentGraphIntegrationAndInvoke();
        System.out.println("WorkflowBranchCandidateTest passed");
    }

    private static void testBranchRouterRoutesFirstMatchingBranch() {
        TestSession session = new TestSession();
        session.state.values.put("start.a", 5);

        BranchRouter router = new BranchRouter();
        router.addBranch("${start.a} <= 10", List.of("b"), "1");
        router.addBranch("${start.a} > 10", List.of("a"), "2");
        router.setSession(session);

        assertEquals(List.of("b"), router.route(), "first matching branch target");
    }

    private static void testBranchRouterAllTargetsAndExpressionHelpers() {
        TestSession session = new TestSession();
        session.state.values.put("node_start.query", "abcdef");
        session.state.values.put("start.input", Map.of("x", List.of("ok")));

        BranchRouter router = new BranchRouter();
        router.addBranch("length(${node_start.query}) >= 5", List.of("llm2"), "if");
        router.addBranch("is_not_empty(${start.input}['x'][0])", List.of("llm1"), "default");
        router.setSession(session);

        assertEquals(Set.of("llm2", "llm1"), router.allTargets(), "all branch targets");
        assertEquals(List.of("llm2"), router.route(), "length expression route");
    }

    private static void testBranchRouterReportsNoMatchingBranch() {
        TestSession session = new TestSession();
        BranchRouter router = new BranchRouter();
        router.addBranch("False", "never", "no");
        router.setSession(session);
        expectBaseError(StatusCode.COMPONENT_BRANCH_EXECUTION_ERROR, router::route);
    }

    private static void testBranchComponentValidation() {
        BranchComponent component = new BranchComponent();
        expectBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> component.addBranch((String) null, "a", ""));
        expectBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> component.addBranch("sss", "", ""));
        expectBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> component.addBranch("sss", (String) null, ""));
        expectBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> component.addBranch("sss", java.util.Arrays.asList("", "xxx"), ""));
        expectBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> component.addBranch("sss", java.util.Arrays.asList("xxx", null), ""));
    }

    private static void testBranchComponentGraphIntegrationAndInvoke() {
        TestSession session = new TestSession();
        session.state.values.put("a", 11);

        BranchComponent component = new BranchComponent();
        component.addBranch("${a} <= 10", List.of("b"), "1");
        component.addBranch("${a} > 10", List.of("a"), "2");

        RecordingGraph graph = new RecordingGraph();
        component.addComponent(graph, "branch", true);

        assertTrue(graph.nodes.get("branch") == component, "component added as executable");
        assertTrue(graph.lastWaitForAll, "wait_for_all forwarded");
        assertTrue(graph.conditionalEdges.get("branch") == component.router(), "router registered as conditional edge");
        assertEquals(Set.of("a", "b"), graph.branchTargets.get("branch"), "branch targets registered");

        Map<String, Object> output = component.invoke(Map.of("ignored", true), session, null);
        assertEquals(Map.of(), output, "BranchComponent.invoke returns empty map");
        assertEquals(List.of("a"), component.router().route(), "router session set by invoke");
        assertTrue(component.skipTrace(), "BranchComponent skips vertex trace");
    }

    private static void expectBaseError(StatusCode status, Runnable runnable) {
        try {
            runnable.run();
        } catch (BaseError error) {
            assertEquals(status, error.getStatus(), "status code");
            return;
        }
        throw new AssertionError("Expected BaseError with status " + status);
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

    public static final class TestSession extends BaseSession {
        private final TestState state = new TestState();
        private final TestTracer tracer = new TestTracer();

        public SessionStateAccess state() {
            return state;
        }

        public TestTracer tracer() {
            return tracer;
        }
    }

    private static final class RecordingGraph extends Graph {
        private final Map<String, Object> nodes = new LinkedHashMap<>();
        private final Map<String, Object> conditionalEdges = new LinkedHashMap<>();
        private final Map<String, Set<String>> branchTargets = new LinkedHashMap<>();
        private boolean lastWaitForAll;

        @Override
        public Graph addNode(String nodeId, com.openjiuwen.core.graph.Executable<?, ?> node, boolean waitForAll) {
            nodes.put(nodeId, node);
            lastWaitForAll = waitForAll;
            return this;
        }

        @Override
        public Graph addConditionalEdges(String sourceNodeId, Object router) {
            conditionalEdges.put(sourceNodeId, router);
            return this;
        }

        public void registerBranchTargets(String nodeId, Set<String> targets) {
            branchTargets.put(nodeId, targets);
        }
    }

    public static final class TestState implements SessionStateAccess {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public Object get(Object key) {
            return values.get(String.valueOf(key));
        }

        @Override
        public void update(Map<String, Object> data) {
            values.putAll(data);
        }

        @Override
        public Object getGlobal(Object path) {
            return values.get(path);
        }
    }

    public static final class TestTracer {
        public void traceComponentBegin(BaseSession session) {
        }

        public void traceComponentInputs(BaseSession session, Map<String, Object> inputs) {
        }

        public void traceComponentOutputs(BaseSession session, Map<String, Object> outputs) {
        }

        public void traceComponentDone(BaseSession session) {
        }
    }
}
