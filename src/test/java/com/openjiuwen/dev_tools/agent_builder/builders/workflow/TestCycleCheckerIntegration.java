/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for CycleChecker module.
 * <p>
 * Mirrors Python's {@code test_cycle_checker_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestCycleCheckerIntegration {

    @Nested
    class TestCycleCheckerIntegrationInner {

        @Test
        void detectsNoCycleInLinearGraph() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("B"));
            graph.put("B", List.of("C"));
            graph.put("C", List.of());

            assertThat(CycleChecker.hasCycle(graph)).isFalse();
        }

        @Test
        void detectsCycleInSimpleGraph() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("B"));
            graph.put("B", List.of("A"));

            assertThat(CycleChecker.hasCycle(graph)).isTrue();
        }

        @Test
        void detectsNoCycleInEmptyGraph() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            assertThat(CycleChecker.hasCycle(graph)).isFalse();
        }

        @Test
        void detectsCycleInSelfLoop() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("A"));

            assertThat(CycleChecker.hasCycle(graph)).isTrue();
        }

        @Test
        void detectsNoCycleInDisconnectedGraph() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());
            graph.put("B", List.of());
            graph.put("C", List.of());

            assertThat(CycleChecker.hasCycle(graph)).isFalse();
        }
    }
}
