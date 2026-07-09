/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency graph helper functions and constants.
 * <p>
 * Mirrors Python tools/database/graph.py: cycle detection,
 * terminal statuses, and dependency rejection statuses for
 * task dependency graph operations.
 * </p>
 * 
 * @since 0.1.7
 */
public final class GraphUtils {
    /**
     * TASK_TERMINAL_STATUSES.
     * 
     * @since 0.1.7
     */
    public static final Set<String> TASK_TERMINAL_STATUSES = Set.of("completed", "cancelled");

    /**
     * TASK_DEPENDENCY_REJECT_STATUSES.
     * 
     * @since 0.1.7
     */
    public static final Set<String> TASK_DEPENDENCY_REJECT_STATUSES =
        Set.of("completed", "cancelled", "claimed", "plan_approved");

    private static final int WHITE = 0;
    private static final int GRAY = 1;
    private static final int BLACK = 2;

    /**
     * GraphUtils.
     * 
     * @since 0.1.7
     */
    private GraphUtils() {
    }

    /**
     * Detect a cycle in a task-dependency adjacency map.
     * <p>
     * The map points from a task to the tasks it depends on
     * ({@code taskId -> [dependsOnTaskId, ...]}). The walk follows edges
     * in that direction; reaching an ancestor node in the current DFS
     * path means the dependency chain loops back on itself.
     * </p>
     * <p>
     * Uses iterative DFS with WHITE/GRAY/BLACK coloring to keep
     * recursion depth bounded for deep dependency chains.
     * </p>
     * 
     * @param adjacency outgoing-edge adjacency map (taskId -> dependsOn list)
     * @return the cycle as a list of task IDs (repeated node at both ends,
     *         e.g. [A, B, C, A]), or {@code null} if the graph is acyclic
     * @since 0.1.7
     */
    public static List<String> detectCycleInAdjacency(Map<String, List<String>> adjacency) {
        if (adjacency == null || adjacency.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Initialize colors for all nodes
        Map<String, Integer> color = new LinkedHashMap<>();
        for (var entry : adjacency.entrySet()) {
            color.putIfAbsent(entry.getKey(), WHITE);
            for (String dep : entry.getValue()) {
                color.putIfAbsent(dep, WHITE);
            }
        }

        for (String root : new ArrayList<>(color.keySet())) {
            if (color.get(root) != WHITE) {
                continue;
            }

            List<String> path = new ArrayList<>();
            path.add(root);
            color.put(root, GRAY);

            // Stack: (node, remaining children iterator state)
            List<StackFrame> stack = new ArrayList<>();
            List<String> rootDeps = adjacency.getOrDefault(root, List.of());
            stack.add(new StackFrame(root, new ArrayList<>(rootDeps), 0));

            while (!stack.isEmpty()) {
                StackFrame frame = stack.get(stack.size() - 1);

                if (frame.index >= frame.children.size()) {
                    // All children processed — mark black and pop
                    color.put(frame.node, BLACK);
                    stack.remove(stack.size() - 1);
                    if (!path.isEmpty()) {
                        path.remove(path.size() - 1);
                    }
                    continue;
                }

                String next = frame.children.get(frame.index);
                frame.index++;

                int c = color.getOrDefault(next, WHITE);
                if (c == GRAY) {
                    // Cycle detected: extract the cycle path
                    int idx = path.indexOf(next);
                    List<String> cycle = new ArrayList<>();
                    for (int i = idx; i < path.size(); i++) {
                        cycle.add(path.get(i));
                    }
                    cycle.add(next);
                    return cycle;
                }

                if (c == WHITE) {
                    color.put(next, GRAY);
                    path.add(next);
                    List<String> nextDeps = adjacency.getOrDefault(next, List.of());
                    stack.add(new StackFrame(next, new ArrayList<>(nextDeps), 0));
                }
                // BLACK nodes are skipped
            }
        }

        return java.util.Collections.emptyList();
    }

    private static final class StackFrame {
        final String node;
        final List<String> children;
        int index;

        StackFrame(String node, List<String> children, int index) {
            this.node = node;
            this.children = children;
            this.index = index;
        }
    }
}
