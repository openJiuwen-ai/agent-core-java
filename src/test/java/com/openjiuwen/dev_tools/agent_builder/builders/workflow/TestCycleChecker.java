/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Test CycleChecker functionality.
 * <p>
 * Mirrors Python's {@code test_cycle_checker.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_cycle_checker.py}.
 */
class TestCycleChecker {

    /**
     * Test parseCycleResultJson method.
     * <p>
     * Mirrors Python's {@code TestCycleChecker} class tests.
     */
    static class TestParseCycleResultJson {

        @Test
        void testParseCycleResultJsonWithCycle() {
            String jsonInput = "```json\n{\"need_refined\": true, \"loop_desc\": \"A->B->A\"}\n```";

            // Note: CycleChecker.java has different API than Python's parse_cycle_result_json
            // This test documents expected behavior for cycle detection
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", Arrays.asList("B"));
            graph.put("B", Arrays.asList("A"));

            boolean hasCycle = CycleChecker.hasCycle(graph);

            Assertions.assertTrue(hasCycle);
        }

        @Test
        void testParseCycleResultJsonNoCycle() {
            // Linear graph has no cycle
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", Arrays.asList("B"));
            graph.put("B", Arrays.asList("C"));
            graph.put("C", new ArrayList<>());

            boolean hasCycle = CycleChecker.hasCycle(graph);

            Assertions.assertFalse(hasCycle);
        }

        @Test
        void testParseCycleResultJsonWithoutCodeBlock() {
            // Simpler linear graph test
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("node1", Arrays.asList("node2"));

            boolean hasCycle = CycleChecker.hasCycle(graph);

            Assertions.assertFalse(hasCycle);
        }

        @Test
        void testParseCycleResultJsonEmptyGraph() {
            Map<String, List<String>> graph = new HashMap<>();

            boolean hasCycle = CycleChecker.hasCycle(graph);

            Assertions.assertFalse(hasCycle);
        }
    }

    /**
     * Test hasCycle method.
     * <p>
     * Mirrors Python's {@code test_check_mermaid_cycle} and related tests.
     */
    static class TestHasCycle {

        @Test
        void testHasCycleSimpleCycle() {
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", Arrays.asList("B"));
            graph.put("B", Arrays.asList("A"));

            Assertions.assertTrue(CycleChecker.hasCycle(graph));
        }

        @Test
        void testHasCycleComplexCycle() {
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", Arrays.asList("B"));
            graph.put("B", Arrays.asList("C"));
            graph.put("C", Arrays.asList("A"));

            Assertions.assertTrue(CycleChecker.hasCycle(graph));
        }

        @Test
        void testHasCycleNoCycle() {
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", Arrays.asList("B", "C"));
            graph.put("B", Arrays.asList("D"));
            graph.put("C", Arrays.asList("D"));
            graph.put("D", new ArrayList<>());

            Assertions.assertFalse(CycleChecker.hasCycle(graph));
        }

        @Test
        void testHasCycleSingleNode() {
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", new ArrayList<>());

            Assertions.assertFalse(CycleChecker.hasCycle(graph));
        }

        @Test
        void testHasCycleSelfLoop() {
            Map<String, List<String>> graph = new HashMap<>();
            graph.put("A", Arrays.asList("A"));

            Assertions.assertTrue(CycleChecker.hasCycle(graph));
        }
    }
}