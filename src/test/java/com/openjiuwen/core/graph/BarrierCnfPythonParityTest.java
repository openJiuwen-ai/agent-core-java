/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.pregel.BarrierChannel;
import com.openjiuwen.core.graph.pregel.BarrierMessage;
import com.openjiuwen.core.graph.pregel.ChannelManager;
import com.openjiuwen.core.workflow.BranchRouter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_barrier_cnf} in
 * {@code tests/unit_tests/core/graph/test_barrier_cnf.py}.
 */
@DisplayName("Python parity for BarrierChannel CNF support")
class BarrierCnfPythonParityTest {

    @Nested
    @DisplayName("BarrierChannel CNF")
    class BarrierChannelCnfTests {

        @Test
        @DisplayName("Single-element groups require every sender")
        void testBackwardCompatibleAllAnd() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A"), Set.of("B"), Set.of("C")));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("A", channel.getKey()));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("B", channel.getKey()));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("C", channel.getKey()));
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("Any sender in an OR group satisfies that group")
        void testOrGroupAnyOneSuffices() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("LLM_1", "LLM_2")));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("LLM_1", channel.getKey()));
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("Mixed OR and AND groups require one sender from each group")
        void testCnfMixedOrAndAnd() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("LLM_1", "LLM_2"), Set.of("LLM_3")));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("LLM_1", channel.getKey()));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("LLM_3", channel.getKey()));
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("Alternative OR branch sender is sufficient")
        void testCnfOrGroupAlternativePath() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("LLM_1", "LLM_2"), Set.of("LLM_3")));

            channel.accept(new BarrierMessage("LLM_2", channel.getKey()));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("LLM_3", channel.getKey()));
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("Independent OR branches are ANDed together")
        void testMultipleIndependentBranches() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B"), Set.of("C", "D")));

            channel.accept(new BarrierMessage("A", channel.getKey()));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("D", channel.getKey()));
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("No received barrier messages means not ready")
        void testEmptyReceivedNotReady() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B")));

            assertFalse(channel.isReady());
        }

        @Test
        @DisplayName("All senders in one OR group still count as one ready group")
        void testAllSendersInOrGroupComplete() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B"), Set.of("C")));

            channel.accept(new BarrierMessage("A", channel.getKey()));
            channel.accept(new BarrierMessage("B", channel.getKey()));
            assertFalse(channel.isReady());

            channel.accept(new BarrierMessage("C", channel.getKey()));
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("Consume clears the received sender state")
        void testConsumeResetsState() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B")));

            channel.accept(new BarrierMessage("A", channel.getKey()));
            assertTrue(channel.isReady());

            channel.consume();
            assertFalse(channel.isReady());
            assertEquals(0, ((List<?>) channel.snapshot()).size());
        }

        @Test
        @DisplayName("Snapshot and restore preserve CNF readiness")
        void testSnapshotRestoreCnf() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B"), Set.of("C")));

            channel.accept(new BarrierMessage("A", channel.getKey()));
            channel.accept(new BarrierMessage("C", channel.getKey()));
            assertTrue(channel.isReady());

            Object snapshot = channel.snapshot();
            assertEquals(Set.of("A", "C"), new LinkedHashSet<>((List<?>) snapshot));

            channel.consume();
            assertFalse(channel.isReady());

            channel.restore(snapshot);
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("Router key uses ampersand for singleton groups")
        void testRouterKeyFormatSingleGroups() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A"), Set.of("B"), Set.of("C")));

            assertEquals("barrier:A&B&C->end", channel.getKey());
        }

        @Test
        @DisplayName("Router key wraps OR groups")
        void testRouterKeyFormatOrGroups() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B"), Set.of("C")));

            assertEquals("barrier:(A|B)&C->end", channel.getKey());
        }

        @Test
        @DisplayName("Router key wraps multiple OR groups")
        void testRouterKeyFormatMultipleOrGroups() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("A", "B"), Set.of("C", "D")));

            assertEquals("barrier:(A|B)&(C|D)->end", channel.getKey());
        }

        @Test
        @DisplayName("ChannelManager marks CNF barrier target ready")
        void testChannelManagerWithCnfBarrier() {
            BarrierChannel barrier = new BarrierChannel("end", List.of(Set.of("LLM_1", "LLM_2")));
            ChannelManager manager = new ChannelManager(List.of(barrier));

            manager.bufferMessage(new BarrierMessage("LLM_1", barrier.getKey()));
            manager.flush();

            assertTrue(barrier.isReady());
            assertTrue(manager.getReadyNodes().contains("end"));
        }

        @Test
        @DisplayName("Restored old checkpoint state is interpreted through CNF groups")
        void testCheckpointRecoveryDeadlockFix() {
            BarrierChannel channel = new BarrierChannel("end", List.of(Set.of("LLM_3"), Set.of("LLM_1", "LLM_2")));

            channel.restore(List.of("LLM_1", "LLM_3"));

            assertTrue(channel.isReady());
        }
    }

    @Nested
    @DisplayName("PregelGraph resolveBarrierGroups")
    class PregelGraphResolveBarrierGroupsTests {

        @Test
        @DisplayName("Missing branch targets returns the original source list")
        void testNoBranchTargetsPassthrough() {
            List<Set<String>> sourceList = List.of(Set.of("A"), Set.of("B"), Set.of("C"));
            PregelGraph graph = new PregelGraph();

            assertEquals(sourceList, graph.resolveBarrierGroups("end", sourceList));
        }

        @Test
        @DisplayName("Predecessors owned by one branch merge into one OR group")
        void testBranchTargetsMergeOrGroup() {
            PregelGraph graph = new PregelGraph()
                    .addEdge("branch_1", "LLM_1")
                    .addEdge("branch_1", "LLM_2")
                    .addEdge("LLM_1", "end")
                    .addEdge("LLM_2", "end")
                    .registerBranchTargets("branch_1", Set.of("LLM_1", "LLM_2"));

            List<Set<String>> result = graph.resolveBarrierGroups("end", List.of(Set.of("LLM_1"), Set.of("LLM_2")));

            assertEquals(1, result.size());
            assertEquals(Set.of("LLM_1", "LLM_2"), result.get(0));
        }

        @Test
        @DisplayName("Branch predecessors and standalone predecessors are kept separate")
        void testBranchWithStandaloneNode() {
            PregelGraph graph = new PregelGraph()
                    .addEdge("branch_1", "LLM_1")
                    .addEdge("branch_1", "LLM_2")
                    .addEdge("LLM_1", "end")
                    .addEdge("LLM_2", "end")
                    .addEdge("LLM_3", "end")
                    .registerBranchTargets("branch_1", Set.of("LLM_1", "LLM_2"));

            List<Set<String>> result = graph.resolveBarrierGroups(
                    "end", List.of(Set.of("LLM_1"), Set.of("LLM_2"), Set.of("LLM_3")));

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(group -> group.size() > 1).count());
            assertEquals(1, result.stream().filter(group -> group.size() == 1).count());
            assertTrue(result.contains(Set.of("LLM_1", "LLM_2")));
            assertTrue(result.contains(Set.of("LLM_3")));
        }

        @Test
        @DisplayName("Two independent branches produce two OR groups")
        void testMultipleIndependentBranches() {
            PregelGraph graph = new PregelGraph()
                    .addEdge("branch_1", "A")
                    .addEdge("branch_1", "B")
                    .addEdge("branch_2", "C")
                    .addEdge("branch_2", "D")
                    .addEdge("A", "end")
                    .addEdge("B", "end")
                    .addEdge("C", "end")
                    .addEdge("D", "end")
                    .registerBranchTargets("branch_1", Set.of("A", "B"))
                    .registerBranchTargets("branch_2", Set.of("C", "D"));

            List<Set<String>> result = graph.resolveBarrierGroups(
                    "end", List.of(Set.of("A"), Set.of("B"), Set.of("C"), Set.of("D")));

            assertEquals(2, result.size());
            assertTrue(result.contains(Set.of("A", "B")));
            assertTrue(result.contains(Set.of("C", "D")));
        }

        @Test
        @DisplayName("Only branch targets that reach the target predecessor list contribute")
        void testPartialBranchTargetsToEnd() {
            PregelGraph graph = new PregelGraph()
                    .addEdge("branch", "A")
                    .addEdge("branch", "B")
                    .addEdge("branch", "C")
                    .addEdge("A", "end")
                    .addEdge("B", "end")
                    .addEdge("C", "X")
                    .registerBranchTargets("branch", Set.of("A", "B", "C"));

            List<Set<String>> result = graph.resolveBarrierGroups("end", List.of(Set.of("A"), Set.of("B")));

            assertEquals(1, result.size());
            assertEquals(Set.of("A", "B"), result.get(0));
        }

        @Test
        @DisplayName("Empty source list returns empty source list")
        void testEmptySourceList() {
            PregelGraph graph = new PregelGraph().registerBranchTargets("b", Set.of("A", "B"));

            assertEquals(List.of(), graph.resolveBarrierGroups("end", List.of()));
        }

        @Test
        @DisplayName("Forward reachable helper mirrors Python BFS fixture")
        void testForwardReachable() {
            List<SimpleEdge> edges = List.of(
                    new SimpleEdge("start", "A"),
                    new SimpleEdge("A", "B"),
                    new SimpleEdge("B", "end"),
                    new SimpleEdge("start", "C")
            );

            assertEquals(Set.of("A", "B", "end"), forwardReachable(edges, "A"));
            assertEquals(Set.of("C"), forwardReachable(edges, "C"));
            assertEquals(Set.of("start", "A", "B", "C", "end"), forwardReachable(edges, "start"));
        }
    }

    @Nested
    @DisplayName("BranchRouter allTargets")
    class BranchRouterAllTargetsTests {

        @Test
        @DisplayName("Single-target branches contribute every target")
        void testAllTargetsSingleTargets() {
            BranchRouter router = new BranchRouter();
            router.addBranch("True", "LLM_1", "default");
            router.addBranch("False", "LLM_2", "if");

            assertEquals(Set.of("LLM_1", "LLM_2"), router.allTargets());
        }

        @Test
        @DisplayName("Multi-target branches flatten all targets")
        void testAllTargetsMultiTargets() {
            BranchRouter router = new BranchRouter();
            router.addBranch("True", List.of("A", "B"), "path1");
            router.addBranch("False", List.of("C"), "path2");

            assertEquals(Set.of("A", "B", "C"), router.allTargets());
        }

        @Test
        @DisplayName("Single branch with one target yields a one-element target set")
        void testAllTargetsSingleBranch() {
            BranchRouter router = new BranchRouter();
            router.addBranch("True", "A", "only");

            assertEquals(Set.of("A"), router.allTargets());
        }
    }

    private static Set<String> forwardReachable(List<SimpleEdge> edges, String startNode) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNode);
        while (!queue.isEmpty()) {
            String node = queue.remove();
            if (!visited.add(node)) {
                continue;
            }
            for (SimpleEdge edge : edges) {
                if (edge.source().equals(node) && !visited.contains(edge.target())) {
                    queue.add(edge.target());
                }
            }
        }
        return visited;
    }

    private record SimpleEdge(String source, String target) {
    }
}
