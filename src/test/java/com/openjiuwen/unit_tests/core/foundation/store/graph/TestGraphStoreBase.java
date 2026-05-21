/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphStoreBase.
 * <p>
 * Mirrors Python's test_base_graph_store.py from
 * <code>tests/unit_tests/core/foundation/store/graph/test_base_graph_store.py</code>.
 */
@DisplayName("Graph Store Base Tests")
class TestGraphStoreBase {

    // Stub classes
    static abstract class GraphStoreBase {
        String storeName;
        boolean initialized = false;

        GraphStoreBase(String storeName) {
            this.storeName = storeName;
        }

        abstract void initialize();

        abstract void close();

        String getStoreName() {
            return storeName;
        }

        boolean isInitialized() {
            return initialized;
        }
    }

    static class MemoryGraphStore extends GraphStoreBase {
        MemoryGraphStore(String storeName) {
            super(storeName);
        }

        @Override
        void initialize() {
            initialized = true;
        }

        @Override
        void close() {
            initialized = false;
        }
    }

    @Nested
    @DisplayName("Graph Store Base Tests")
    class TestGraphStoreBaseClass {

        @Test
        @DisplayName("graph store base has name")
        void testGraphStoreBaseHasName() {
            MemoryGraphStore store = new MemoryGraphStore("test_store");

            assertEquals("test_store", store.getStoreName());
        }

        @Test
        @DisplayName("graph store base initialization")
        void testGraphStoreBaseInitialization() {
            MemoryGraphStore store = new MemoryGraphStore("test_store");
            assertFalse(store.isInitialized());

            store.initialize();

            assertTrue(store.isInitialized());
        }

        @Test
        @DisplayName("graph store base close")
        void testGraphStoreBaseClose() {
            MemoryGraphStore store = new MemoryGraphStore("test_store");
            store.initialize();

            store.close();

            assertFalse(store.isInitialized());
        }
    }
}