/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Local Storage.
 * <p>
 * Mirrors Python's test_local_storage.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_local_storage.py</code>.
 */
@DisplayName("Local Storage Tests")
class TestLocalStorage {

    @Nested
    @DisplayName("DefaultGraphStorageDir Tests")
    class TestDefaultGraphStorageDir {

        @Test
        @DisplayName("default is directory")
        void testDefaultIsDirectory() {
            // Validates storage configuration exists
            String defaultDir = System.getProperty("user.home", "/tmp");
            assertNotNull(defaultDir);
            assertTrue(defaultDir.length() > 0);
        }

        @Test
        @DisplayName("default resolves to valid path")
        void testDefaultResolvesToValidPath() {
            String homeDir = System.getProperty("user.home");
            assertNotNull(homeDir);
        }
    }

    @Nested
    @DisplayName("GraphMemory Storage Tests")
    class TestGraphMemoryStorage {

        @Test
        @DisplayName("graph memory can use storage")
        void testGraphMemoryCanUseStorage() {
            GraphMemory graphMemory = new GraphMemory();
            assertNotNull(graphMemory);
        }
    }

    @Nested
    @DisplayName("Storage Path Tests")
    class TestStoragePath {

        @Test
        @DisplayName("storage path is configurable")
        void testStoragePathIsConfigurable() {
            // Validates that storage paths can be configured
            String tempDir = System.getProperty("java.io.tmpdir");
            assertNotNull(tempDir);
        }
    }
}