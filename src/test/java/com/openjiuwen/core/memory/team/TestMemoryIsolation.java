/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolation of per-member memory directories and index managers.
 * Mirrors Python's tests/unit_tests/core/memory/team/test_memory_isolation.py.
 */
@DisplayName("Memory isolation tests")
class TestMemoryIsolation {

    @TempDir
    Path tempDir;

    @Test
    void testTwoMembersDistinctManagersAndDiskPaths() throws Exception {
        Path rootA = tempDir.resolve("ws_a");
        Path rootB = tempDir.resolve("ws_b");
        Files.createDirectories(rootA);
        Files.createDirectories(rootB);

        MemberMemoryToolkit toolkitA =
                new MemberMemoryToolkit("m1", "same_team", new MockWorkspace(rootA), "general");
        MemberMemoryToolkit toolkitB =
                new MemberMemoryToolkit("m2", "same_team", new MockWorkspace(rootB), "general");

        assertTrue(toolkitA.initialize().get());
        assertTrue(toolkitB.initialize().get());

        assertNotNull(toolkitA.getManager());
        assertNotNull(toolkitB.getManager());
        assertNotSame(toolkitA.getManager(), toolkitB.getManager());

        Path marker = rootA.resolve("memory").resolve("m1_exclusive.txt");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "only-a");

        Path otherPath = rootB.resolve("memory").resolve("m1_exclusive.txt");
        assertFalse(Files.isRegularFile(otherPath));

        toolkitA.close().get();
        toolkitB.close().get();
    }

    private static final class MockWorkspace {
        private final Path root;

        private MockWorkspace(Path root) {
            this.root = root;
        }

        public Path getNodePath(String nodeName) throws IOException {
            Path nodePath = root.resolve(nodeName);
            Files.createDirectories(nodePath);
            return nodePath;
        }
    }
}
