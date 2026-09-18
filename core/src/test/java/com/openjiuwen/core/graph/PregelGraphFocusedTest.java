/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.pregel.BarrierChannel;
import com.openjiuwen.core.graph.pregel.Channel;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Focused validation for {@link PregelGraph}.
 *
 * <p>Mirrors Python's {@code PregelGraph}, {@code CompiledGraph}, {@code Branch},
 * and {@code _get_callable_name} in {@code openjiuwen/core/graph/graph.py}.</p>
 */
public final class PregelGraphFocusedTest {

    private PregelGraphFocusedTest() {
    }

    public static void main(String[] args) {
        graphBuildsBranchAwareBarrierGroups();
        branchAdaptsRouterAndSupplierCallables();
        compiledGraphPassBodiesRemainNullOrNoOp();
        System.out.println("PASS PregelGraphFocusedTest");
    }

    private static void graphBuildsBranchAwareBarrierGroups() {
        PregelGraph graph = new PregelGraph();
        RecordingExecutable executable = new RecordingExecutable();
        graph.addNode("branch", executable);
        graph.addNode("left", executable);
        graph.addNode("right", executable);
        graph.addNode("join", executable, true);
        graph.addEdge(PregelConstants.START, "branch");
        graph.addEdge("branch", "left");
        graph.addEdge("branch", "right");
        graph.addEdge("left", "join");
        graph.addEdge("right", "join");
        graph.registerBranchTargets("branch", Set.of("left", "right"));
        graph.addConditionalEdges("branch", (Router) ignored -> List.of("left", "right"));

        CompiledGraph compiled = (CompiledGraph) graph.compile(new RecordingSession(), Map.of());

        boolean hasOrBarrier = compiled.getPregel().getChannels().stream()
                .filter(BarrierChannel.class::isInstance)
                .map(Channel::getKey)
                .anyMatch(key -> key.contains("(left|right)->join") || key.contains("(right|left)->join"));
        require(hasOrBarrier, "branch targets should become one OR barrier group");
        require(graph.getNodes().containsKey("branch"), "getNodes exposes vertices");
        require(graph.getWaits().contains("join"), "wait_for_all node tracked");
        require(graph.getBranchTargets().get("branch").contains("left"), "branch target registered");
    }

    private static void branchAdaptsRouterAndSupplierCallables() {
        Branch routerBranch = new Branch((Router) ignored -> "next");
        require("next".equals(routerBranch.route()), "router branch route");

        Branch supplierBranch = new Branch((Supplier<List<String>>) () -> List.of("a", "b"));
        require(List.of("a", "b").equals(supplierBranch.route()), "supplier branch route");

        String name = PregelGraph.getCallableName((Router) ignored -> "next");
        require(name != null && !name.isBlank(), "callable name fallback");
    }

    private static void compiledGraphPassBodiesRemainNullOrNoOp() {
        CompiledGraph graph = new CompiledGraph(
                new PregelGraph().compilePregel(null, null),
                new RecordingCheckpointer()
        );
        require(graph.stream(Map.of(), new RecordingSession()) == null, "stream pass body returns null");
        graph.interrupt(Map.of("message", "stop"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingExecutable extends Executable<Map<String, Object>, Map<String, Object>> {
        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs, com.openjiuwen.core.session.BaseSession session,
                                            Object... kwargs) {
            return Map.of();
        }
    }

    private static final class RecordingSession extends Vertex.VertexSession
            implements CompiledGraph.GraphRuntimeSession {
        private final RecordingState state = new RecordingState();
        private final RecordingCheckpointer checkpointer = new RecordingCheckpointer();

        @Override
        public Vertex.VertexState state() {
            return state;
        }

        @Override
        public String sessionId() {
            return "session-1";
        }

        @Override
        public String workflowId() {
            return "workflow-1";
        }

        @Override
        public CompiledGraph.WorkflowState workflowState() {
            return state;
        }

        @Override
        public CompiledGraph.GraphCheckpointer checkpointer() {
            return checkpointer;
        }

    }

    private static final class RecordingState
            implements Vertex.VertexState, CompiledGraph.WorkflowState {
        private int commitCount;

        @Override
        public Map<String, Object> getInputs(Object schema) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getInputsByTransformer(Vertex.ValueTransformer transformer) {
            return Map.of();
        }

        @Override
        public Object getOutputs(String nodeId) {
            return null;
        }

        @Override
        public void setOutputs(Map<String, Object> outputs) {
        }

        @Override
        public Object getWorkflowState(String key) {
            return null;
        }

        @Override
        public void updateAndCommitWorkflowState(Map<String, Object> data) {
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        public Object get(String key) {
            return null;
        }

        @Override
        public void update(Map<String, Object> data) {
        }

        @Override
        public void commitUserInputs(Object inputs) {
        }

        @Override
        public void commit() {
            commitCount++;
        }
    }

    private static final class RecordingCheckpointer implements CompiledGraph.GraphCheckpointer {
    }
}
