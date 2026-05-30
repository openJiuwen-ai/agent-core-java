/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test history manager functionality.
 * <p>
 * Mirrors Python's {@code test_history_manager.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/executor/test_history_manager.py}.
 *
 */
class TestHistoryManager {

    /**
     * Test HistoryManager initialization.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            HistoryManager manager = new HistoryManager();

            assertNotNull(manager.getHistory());
            assertTrue(manager.getHistory().isEmpty());
        }
    }

    /**
     * Test HistoryManager add history.
     */
    @Nested
    class TestAddHistory {

        @Test
        void testAddHistorySuccess() {
            HistoryManager manager = new HistoryManager();
            manager.addEntry(Map.of("role", "user", "content", "Hello"));

            assertEquals(1, manager.getHistory().size());
            assertEquals("Hello", manager.getHistory().get(0).get("content"));
        }

        @Test
        void testAddHistoryWithNull() {
            HistoryManager manager = new HistoryManager();

            assertThrows(NullPointerException.class, () -> manager.addEntry(null));
        }
    }

    /**
     * Test HistoryManager get history.
     */
    @Nested
    class TestGetHistory {

        @Test
        void testGetHistorySuccess() {
            HistoryManager manager = new HistoryManager();
            manager.addEntry(Map.of("role", "user", "content", "Hello"));
            manager.addEntry(Map.of("role", "assistant", "content", "Hi"));

            assertEquals(2, manager.getHistory().size());
        }

        @Test
        void testGetHistoryEmpty() {
            assertTrue(new HistoryManager().getHistory().isEmpty());
        }
    }
}
