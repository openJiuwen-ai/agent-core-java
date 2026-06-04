/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
            String defaultDir = LocalStorage.DEFAULT_GRAPH_STORAGE_DIR;
            assertNotNull(defaultDir);
            assertTrue(defaultDir.length() > 0);
            assertTrue(Path.of(defaultDir).isAbsolute());
        }

        @Test
        @DisplayName("default resolves to valid path")
        void testDefaultResolvesToValidPath() {
            String expected = Path.of("src", "main", "java", "com", "openjiuwen", "core", "memory",
                    "graph", "graph_memory").toAbsolutePath().normalize().toString();
            assertEquals(expected, Path.of(LocalStorage.DEFAULT_GRAPH_STORAGE_DIR).normalize().toString());
        }
    }
}
