/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import java.util.*;

/**
 * Dependency graph helper functions and constants.
 * <p>
 * Mirrors Python's {@code graph} module in
 * {@code openjiuwen.agent_teams.tools.database.graph}.
 */
public class GraphDatabase {

    // Terminal statuses for tasks
    public static final Set<String> TASK_TERMINAL_STATUSES = Set.of(
        "completed",
        "cancelled"
    );

    // Reject statuses for task dependencies
    public static final Set<String> TASK_DEPENDENCY_REJECT_STATUSES = Set.of(
        "completed",
        "cancelled",
        "claimed",
        "plan_approved"
    );

    /**
     * Detect a cycle in a task-dependency adjacency map.
     * <p>
     * The map points from a task to the tasks it depends on
     * (task_id -> [depends_on_task_id, ...]). The walk follows edges
     * in that direction; reaching an ancestor node in the current DFS
     * path means the dependency chain loops back on itself.
     * <p>
     * Uses iterative DFS with WHITE/GRAY/BLACK coloring to keep
     * recursion depth bounded for deep dependency chains.
     *
     * @param adjacency Outgoing-edge adjacency map
     * @return The cycle as a list of task IDs (the repeated node appears
     *         at both ends, e.g. [A, B, C, A]), or null if acyclic
     */
    public static List<String> detectCycleInAdjacency(Map<String, List<String>> adjacency) {
        // Color constants: WHITE=0 (unvisited), GRAY=1 (visiting), BLACK=2 (done)
        final int WHITE = 0, GRAY = 1, BLACK = 2;
        Map<String, Integer> color = new HashMap<>();

        // Initialize all nodes as WHITE
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            color.put(entry.getKey(), WHITE);
            for (String dep : entry.getValue()) {
                color.putIfAbsent(dep, WHITE);
            }
        }

        // Iterative DFS
        for (String root : new ArrayList<>(color.keySet())) {
            if (color.getOrDefault(root, WHITE) != WHITE) {
                continue;
            }

            List<String> path = new ArrayList<>();
            path.add(root);
            color.put(root, GRAY);

            // Stack of (node, children iterator)
            Deque<Map.Entry<String, Iterator<String>>> stack = new ArrayDeque<>();
            stack.push(Map.entry(root, adjacency.getOrDefault(root, List.of()).iterator()));

            while (!stack.isEmpty()) {
                Map.Entry<String, Iterator<String>> current = stack.peek();
                String node = current.getKey();
                Iterator<String> children = current.getValue();

                if (!children.hasNext()) {
                    stack.pop();
                    color.put(node, BLACK);
                    path.remove(path.size() - 1);
                    continue;
                }

                String next = children.next();
                int c = color.getOrDefault(next, WHITE);

                if (c == GRAY) {
                    // Found cycle - extract it from path
                    int idx = path.indexOf(next);
                    List<String> cycle = new ArrayList<>(path.subList(idx, path.size()));
                    cycle.add(next);
                    return cycle;
                }

                if (c == WHITE) {
                    color.put(next, GRAY);
                    path.add(next);
                    stack.push(Map.entry(next, 
                        adjacency.getOrDefault(next, List.of()).iterator()));
                }
            }
        }

        return null;  // No cycle found
    }

    /**
     * Check if the adjacency map contains a cycle.
     *
     * @param adjacency Outgoing-edge adjacency map
     * @return true if a cycle exists
     */
    public static boolean hasCycle(Map<String, List<String>> adjacency) {
        return detectCycleInAdjacency(adjacency) != null;
    }

    /**
     * Get all nodes reachable from a starting node.
     *
     * @param adjacency Outgoing-edge adjacency map
     * @param start     Starting node
     * @return Set of reachable nodes
     */
    public static Set<String> getReachableNodes(Map<String, List<String>> adjacency, String start) {
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            String node = stack.pop();
            if (visited.contains(node)) {
                continue;
            }
            visited.add(node);
            for (String dep : adjacency.getOrDefault(node, List.of())) {
                if (!visited.contains(dep)) {
                    stack.push(dep);
                }
            }
        }

        return visited;
    }

    /**
     * Get all dependencies of a task (transitive closure).
     *
     * @param adjacency Outgoing-edge adjacency map
     * @param taskId    Task to get dependencies for
     * @return Set of all transitive dependencies
     */
    public static Set<String> getAllDependencies(Map<String, List<String>> adjacency, String taskId) {
        return getReachableNodes(adjacency, taskId);
    }
}