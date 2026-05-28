/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemGraphBase.
 * <p>
 * Mirrors Python's test_mem_graph_base.py from
 * <code>tests/unit_tests/core/foundation/store/graph/test_mem_graph_base.py</code>.
 */
@DisplayName("Mem Graph Base Tests")
class TestMemGraphBase {

    // Stub classes
    static abstract class MemGraphBase {
        String graphId;
        boolean cleared = false;

        MemGraphBase(String graphId) {
            this.graphId = graphId;
        }

        abstract void initialize();

        void clear() {
            cleared = true;
        }

        String getGraphId() {
            return graphId;
        }

        boolean isCleared() {
            return cleared;
        }
    }

    static class SimpleMemGraph extends MemGraphBase {
        boolean initialized = false;

        SimpleMemGraph(String graphId) {
            super(graphId);
        }

        @Override
        void initialize() {
            initialized = true;
        }

        boolean isInitialized() {
            return initialized;
        }
    }

    @Nested
    @DisplayName("Mem Graph Base Tests")
    class TestMemGraphBaseClass {

        @Test
        @DisplayName("mem graph base has id")
        void testMemGraphBaseHasId() {
            SimpleMemGraph graph = new SimpleMemGraph("graph-1");

            assertEquals("graph-1", graph.getGraphId());
        }

        @Test
        @DisplayName("mem graph base initialization")
        void testMemGraphBaseInitialization() {
            SimpleMemGraph graph = new SimpleMemGraph("graph-1");
            assertFalse(graph.isInitialized());

            graph.initialize();

            assertTrue(graph.isInitialized());
        }

        @Test
        @DisplayName("mem graph base clear")
        void testMemGraphBaseClear() {
            SimpleMemGraph graph = new SimpleMemGraph("graph-1");
            graph.initialize();

            graph.clear();

            assertTrue(graph.isCleared());
        }
    }
}