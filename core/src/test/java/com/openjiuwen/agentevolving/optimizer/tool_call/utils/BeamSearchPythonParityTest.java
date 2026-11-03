/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_beam_search} module in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_beam_search.py}.
 */
class BeamSearchPythonParityTest {

    @Test
    void treeNodeDepthAndRepr() {
        TreeNode root = new TreeNode("r", 1.0d, Map.of("x", 1));
        TreeNode child = new TreeNode("c", 2.0d, Map.of("x", 2), root.getHistory());
        root.addChild(child);

        assertEquals(0, root.getDepth());
        assertEquals(1, child.getDepth());
        assertTrue(root.toString().contains("it=0 score=1.0 data=\"r\""));
    }

    @Test
    void beamSearchSearchAndPrune() {
        BeamSearch search = new BeamSearch(new DummyMethod(), 1, 2, 2, 1, false, false, false, 100.0d, 1);

        List<List<Object>> result = search.search(Map.of("name", "tool"));

        assertEquals(1, result.size());
        assertEquals(Map.of("it", 0), result.get(0).get(0));
        assertEquals(Map.of("it", 2), result.get(0).get(result.get(0).size() - 1));

        List<TreeNode> nodes = List.of(
                new TreeNode("a", 1.0d, Map.of()),
                new TreeNode("b", 3.0d, Map.of()),
                new TreeNode("c", 2.0d, Map.of())
        );
        List<TreeNode> pruned = search.prune(nodes);

        assertEquals(List.of(3.0d), pruned.stream().map(TreeNode::getScore).toList());
    }

    @Test
    void beamSearchTimeoutAndEarlyStop() {
        BeamSearch search = new BeamSearch(new DummyMethod(), 1, 1, 3, 1, false, true, false, 1.0d, 1);
        search.setTimeoutMs(-1L);

        List<List<Object>> result = search.search(Map.of("name", "tool"));

        assertEquals(1, result.size());
        assertEquals(Map.of("it", 0), result.get(0).get(0));
        assertTrue(search.checkEarlyStop(List.of(new TreeNode("x", 2.0d, Map.of())), 1.0d, 1));
        assertFalse(search.checkEarlyStop(List.of(), 1.0d, 1));
    }

    @Test
    void beamSearchInvalidRootAndExpandError() {
        BeamSearch search = new BeamSearch(new InvalidMethod(), 1, 1, 1, 1, false, true, true, 100.0d, 1);

        assertThrows(RuntimeException.class, () -> search.search(Map.of("name", "tool")));

        TreeNode root = new TreeNode("r", 1.0d, Map.of("ok", 1));
        assertThrows(RuntimeException.class, () -> search.expand(List.of(root), Map.of("name", "tool"), null, 1));
    }

    /**
     * Mirrors Python's {@code DummyMethod} in
     * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_beam_search.py}.
     */
    static final class DummyMethod {

        public BeamSearch.StepResult step(
                Map<String, Object> tool,
                List<Object> examples,
                List<Object> previousOutputs,
            int depth
        ) {
            if (depth == 0) {
                return new BeamSearch.StepResult("root", 1.0d, Map.of("it", 0));
            }
            return new BeamSearch.StepResult("node-" + depth, depth + 1.0d, Map.of("it", depth));
        }
    }

    /**
     * Mirrors Python's {@code InvalidMethod} in
     * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_beam_search.py}.
     */
    static final class InvalidMethod {

        public BeamSearch.StepResult step(
                Map<String, Object> tool,
                List<Object> examples,
                List<Object> previousOutputs,
            int depth
        ) {
            return new BeamSearch.StepResult("x", -1.0d, Map.of("bad", true));
        }
    }
}
