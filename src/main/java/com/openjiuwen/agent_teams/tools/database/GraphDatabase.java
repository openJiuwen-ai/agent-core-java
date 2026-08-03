/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency graph helper functions and constants.
 * <p>
 * Mirrors Python's module in
 * {@code openjiuwen/agent_teams/tools/database/graph.py}.
 */
public final class GraphDatabase {

    public static final Set<String> TASK_TERMINAL_STATUSES = Set.of(
            TaskStatus.COMPLETED.value(),
            TaskStatus.CANCELLED.value()
    );

    public static final Set<String> TASK_DEPENDENCY_REJECT_STATUSES = Set.of(
            TaskStatus.COMPLETED.value(),
            TaskStatus.CANCELLED.value(),
            TaskStatus.CLAIMED.value(),
            TaskStatus.PLAN_APPROVED.value()
    );

    private GraphDatabase() {
    }

    public static List<String> detectCycleInAdjacency(Map<String, List<String>> adjacency) {
        final int white = 0;
        final int gray = 1;
        final int black = 2;
        Map<String, Integer> color = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            color.put(entry.getKey(), white);
            for (String dependency : entry.getValue()) {
                color.putIfAbsent(dependency, white);
            }
        }

        for (String root : List.copyOf(color.keySet())) {
            if (color.getOrDefault(root, white) != white) {
                continue;
            }

            List<String> path = new ArrayList<>();
            path.add(root);
            color.put(root, gray);

            Deque<Map.Entry<String, Iterator<String>>> stack = new ArrayDeque<>();
            stack.push(Map.entry(root, adjacency.getOrDefault(root, List.of()).iterator()));

            while (!stack.isEmpty()) {
                Map.Entry<String, Iterator<String>> current = stack.peek();
                String node = current.getKey();
                Iterator<String> children = current.getValue();

                if (!children.hasNext()) {
                    stack.pop();
                    color.put(node, black);
                    path.remove(path.size() - 1);
                    continue;
                }

                String next = children.next();
                int nextColor = color.getOrDefault(next, white);
                if (nextColor == gray) {
                    int index = path.indexOf(next);
                    List<String> cycle = new ArrayList<>(path.subList(index, path.size()));
                    cycle.add(next);
                    return cycle;
                }

                if (nextColor == white) {
                    color.put(next, gray);
                    path.add(next);
                    stack.push(Map.entry(next, adjacency.getOrDefault(next, List.of()).iterator()));
                }
            }
        }

        return null;
    }
}
