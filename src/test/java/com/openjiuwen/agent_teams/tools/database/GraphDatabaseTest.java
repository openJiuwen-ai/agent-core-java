/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphDatabaseTest {

    @Test
    void statusSetsMirrorPythonTaskStatusBuckets() {
        assertThat(GraphDatabase.TASK_TERMINAL_STATUSES)
                .containsExactlyInAnyOrder("completed", "cancelled");
        assertThat(GraphDatabase.TASK_DEPENDENCY_REJECT_STATUSES)
                .containsExactlyInAnyOrder("completed", "cancelled", "claimed", "plan_approved");
    }

    @Test
    void detectCycleInAdjacencyReturnsLoopWithRepeatedTail() {
        Map<String, List<String>> adjacency = Map.of(
                "task-a", List.of("task-b"),
                "task-b", List.of("task-c"),
                "task-c", List.of("task-a")
        );

        List<String> cycle = GraphDatabase.detectCycleInAdjacency(adjacency);

        assertThat(cycle).isNotNull();
        assertThat(cycle).hasSize(4);
        assertThat(cycle.getFirst()).isEqualTo(cycle.getLast());
        assertThat(cycle.subList(0, cycle.size() - 1))
                .containsExactlyInAnyOrder("task-a", "task-b", "task-c");
    }

    @Test
    void detectCycleInAdjacencyReturnsNullForAcyclicGraph() {
        Map<String, List<String>> adjacency = Map.of(
                "task-a", List.of("task-b", "task-c"),
                "task-b", List.of("task-d"),
                "task-c", List.of(),
                "task-d", List.of()
        );

        assertThat(GraphDatabase.detectCycleInAdjacency(adjacency)).isNull();
    }
}
