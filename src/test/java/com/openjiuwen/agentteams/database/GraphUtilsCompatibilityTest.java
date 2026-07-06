package com.openjiuwen.agentteams.database;

import com.openjiuwen.agentteams.tools.database.GraphUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphUtilsCompatibilityTest {

    @Test
    void shouldReturnNullForEmptyAdjacency() {
        assertThat(GraphUtils.detectCycleInAdjacency(null)).isNull();
        assertThat(GraphUtils.detectCycleInAdjacency(Map.of())).isNull();
    }

    @Test
    void shouldReturnNullForAcyclicGraph() {
        // A -> B -> C (no cycle)
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("A", List.of("B"));
        adjacency.put("B", List.of("C"));
        adjacency.put("C", List.of());

        assertThat(GraphUtils.detectCycleInAdjacency(adjacency)).isNull();
    }

    @Test
    void shouldDetectSimpleCycle() {
        // A -> B -> A
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("A", List.of("B"));
        adjacency.put("B", List.of("A"));

        List<String> cycle = GraphUtils.detectCycleInAdjacency(adjacency);
        assertThat(cycle).isNotNull()
                .containsExactly("A", "B", "A");
    }

    @Test
    void shouldDetectThreeNodeCycle() {
        // A -> B -> C -> A
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("A", List.of("B"));
        adjacency.put("B", List.of("C"));
        adjacency.put("C", List.of("A"));

        List<String> cycle = GraphUtils.detectCycleInAdjacency(adjacency);
        assertThat(cycle).isNotNull()
                .containsExactly("A", "B", "C", "A");
    }

    @Test
    void shouldDetectSelfLoop() {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("A", List.of("A"));

        List<String> cycle = GraphUtils.detectCycleInAdjacency(adjacency);
        assertThat(cycle).isNotNull()
                .containsExactly("A", "A");
    }

    @Test
    void shouldHandleDisconnectedAcyclicGraph() {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("A", List.of("B"));
        adjacency.put("C", List.of("D"));
        adjacency.put("B", List.of());
        adjacency.put("D", List.of());

        assertThat(GraphUtils.detectCycleInAdjacency(adjacency)).isNull();
    }

    @Test
    void shouldHandleDiamondDependency() {
        // A -> B, A -> C, B -> D, C -> D (diamond, no cycle)
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("A", List.of("B", "C"));
        adjacency.put("B", List.of("D"));
        adjacency.put("C", List.of("D"));
        adjacency.put("D", List.of());

        assertThat(GraphUtils.detectCycleInAdjacency(adjacency)).isNull();
    }

    @Test
    void shouldDefineTerminalStatuses() {
        assertThat(GraphUtils.TASK_TERMINAL_STATUSES)
                .contains("completed", "cancelled");
    }

    @Test
    void shouldDefineDependencyRejectStatuses() {
        assertThat(GraphUtils.TASK_DEPENDENCY_REJECT_STATUSES)
                .contains("completed", "cancelled", "claimed", "plan_approved");
    }
}
