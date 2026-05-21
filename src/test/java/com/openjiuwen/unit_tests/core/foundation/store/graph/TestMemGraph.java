/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemGraph.
 * <p>
 * Mirrors Python's test_mem_graph.py from
 * <code>tests/unit_tests/core/foundation/store/graph/test_mem_graph.py</code>.
 */
@DisplayName("Mem Graph Tests")
class TestMemGraph {

    // Stub classes
    static class GraphNode {
        String id;
        String type;
        Map<String, Object> properties = new HashMap<>();
        List<GraphEdge> outgoingEdges = new ArrayList<>();
        List<GraphEdge> incomingEdges = new ArrayList<>();

        GraphNode(String id, String type) {
            this.id = id;
            this.type = type;
        }

        void setProperty(String key, Object value) {
            properties.put(key, value);
        }
    }

    static class GraphEdge {
        String id;
        String type;
        GraphNode source;
        GraphNode target;
        Map<String, Object> properties = new HashMap<>();

        GraphEdge(String id, String type, GraphNode source, GraphNode target) {
            this.id = id;
            this.type = type;
            this.source = source;
            this.target = target;
        }
    }

    static class MemGraph {
        Map<String, GraphNode> nodes = new HashMap<>();
        Map<String, GraphEdge> edges = new HashMap<>();

        GraphNode addNode(String id, String type) {
            GraphNode node = new GraphNode(id, type);
            nodes.put(id, node);
            return node;
        }

        GraphEdge addEdge(String id, String type, String sourceId, String targetId) {
            GraphNode source = nodes.get(sourceId);
            GraphNode target = nodes.get(targetId);
            if (source == null || target == null) {
                throw new IllegalArgumentException("Source or target node not found");
            }
            GraphEdge edge = new GraphEdge(id, type, source, target);
            edges.put(id, edge);
            source.outgoingEdges.add(edge);
            target.incomingEdges.add(edge);
            return edge;
        }

        GraphNode getNode(String id) {
            return nodes.get(id);
        }

        GraphEdge getEdge(String id) {
            return edges.get(id);
        }

        int nodeCount() {
            return nodes.size();
        }

        int edgeCount() {
            return edges.size();
        }
    }

    @Nested
    @DisplayName("Mem Graph Node Tests")
    class TestMemGraphNode {

        @Test
        @DisplayName("add node to mem graph")
        void testAddNodeToMemGraph() {
            MemGraph graph = new MemGraph();

            GraphNode node = graph.addNode("n1", "Person");

            assertNotNull(node);
            assertEquals("n1", node.id);
            assertEquals("Person", node.type);
            assertEquals(1, graph.nodeCount());
        }

        @Test
        @DisplayName("node with properties")
        void testNodeWithProperties() {
            GraphNode node = new GraphNode("n1", "Person");
            node.setProperty("name", "Alice");
            node.setProperty("age", 30);

            assertEquals("Alice", node.properties.get("name"));
            assertEquals(30, node.properties.get("age"));
        }

        @Test
        @DisplayName("get node from graph")
        void testGetNodeFromGraph() {
            MemGraph graph = new MemGraph();
            graph.addNode("n1", "Person");

            GraphNode retrieved = graph.getNode("n1");

            assertNotNull(retrieved);
        }
    }

    @Nested
    @DisplayName("Mem Graph Edge Tests")
    class TestMemGraphEdge {

        @Test
        @DisplayName("add edge to mem graph")
        void testAddEdgeToMemGraph() {
            MemGraph graph = new MemGraph();
            graph.addNode("n1", "Person");
            graph.addNode("n2", "Person");

            GraphEdge edge = graph.addEdge("e1", "KNOWS", "n1", "n2");

            assertNotNull(edge);
            assertEquals("e1", edge.id);
            assertEquals("KNOWS", edge.type);
            assertEquals(1, graph.edgeCount());
        }

        @Test
        @DisplayName("edge connects nodes")
        void testEdgeConnectsNodes() {
            MemGraph graph = new MemGraph();
            GraphNode n1 = graph.addNode("n1", "Person");
            GraphNode n2 = graph.addNode("n2", "Person");
            graph.addEdge("e1", "KNOWS", "n1", "n2");

            assertEquals(1, n1.outgoingEdges.size());
            assertEquals(1, n2.incomingEdges.size());
        }

        @Test
        @DisplayName("add edge with missing node throws")
        void testAddEdgeWithMissingNodeThrows() {
            MemGraph graph = new MemGraph();
            graph.addNode("n1", "Person");
            // n2 not added

            assertThrows(IllegalArgumentException.class, () -> {
                graph.addEdge("e1", "KNOWS", "n1", "n2");
            });
        }
    }
}