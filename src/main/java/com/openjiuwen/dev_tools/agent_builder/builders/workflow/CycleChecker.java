/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import java.util.*;

/**
 * Workflow cycle detection.
 * <p>
 * Mirrors Python's {@code CycleChecker} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.cycle_checker}.
 */
public final class CycleChecker {

    private CycleChecker() {
    }

    /** Check if a graph has cycles. */
    public static boolean hasCycle(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (dfs(node, graph, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfs(String node, Map<String, List<String>> graph,
                                Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        inStack.add(node);

        List<String> neighbors = graph.getOrDefault(node, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (dfs(neighbor, graph, visited, inStack)) {
                return true;
            }
        }

        inStack.remove(node);
        return false;
    }
}
