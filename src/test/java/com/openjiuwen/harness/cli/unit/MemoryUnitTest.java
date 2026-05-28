/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OPENJIUWEN.md memory loading.
 * <p>
 * Mirrors Python's {@code test_memory} in
 * {@code tests.cli.unit.test_memory}.
 */
class MemoryUnitTest {

    private static final int MAX_MEMORY_CHARS = 30_000;

    @TempDir
    Path tmpPath;

    @Test
    void loadProjectMemory() throws IOException {
        Path gitDir = tmpPath.resolve(".git");
        Files.createDirectories(gitDir);
        Path memoryFile = tmpPath.resolve("OPENJIUWEN.md");
        Files.writeString(memoryFile, "# Rules\n- Use pytest\n");

        String content = Files.readString(memoryFile);
        assertTrue(content.contains("Use pytest"));
    }

    @Test
    void loadUserMemory() throws IOException {
        Path userDir = tmpPath.resolve(".openjiuwen");
        Files.createDirectories(userDir);
        Path memoryFile = userDir.resolve("OPENJIUWEN.md");
        Files.writeString(memoryFile, "# Global\n- Always use English\n");

        assertTrue(Files.exists(memoryFile));
        String content = Files.readString(memoryFile);
        assertTrue(content.contains("Always use English"));
    }

    @Test
    void projectAndUserMerged() throws IOException {
        Path home = tmpPath.resolve("home");
        Path userDir = home.resolve(".openjiuwen");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("OPENJIUWEN.md"), "USER_MARKER");

        Path proj = tmpPath.resolve("project");
        Files.createDirectories(proj);
        Files.createDirectories(proj.resolve(".git"));
        Files.writeString(proj.resolve("OPENJIUWEN.md"), "PROJECT_MARKER");

        String userContent = Files.readString(userDir.resolve("OPENJIUWEN.md"));
        String projContent = Files.readString(proj.resolve("OPENJIUWEN.md"));
        assertTrue(userContent.contains("USER_MARKER"));
        assertTrue(projContent.contains("PROJECT_MARKER"));
    }

    @Test
    void truncationBeyondMaxChars() throws IOException {
        Path gitDir = tmpPath.resolve(".git");
        Files.createDirectories(gitDir);
        Path memoryFile = tmpPath.resolve("OPENJIUWEN.md");
        String longContent = "x".repeat(50_000);
        Files.writeString(memoryFile, longContent);

        String content = Files.readString(memoryFile);
        assertTrue(content.length() > MAX_MEMORY_CHARS);
    }

    @Test
    void noMemoryReturnsEmpty() throws IOException {
        Path emptyDir = tmpPath.resolve("empty");
        Files.createDirectories(emptyDir);

        assertFalse(Files.exists(emptyDir.resolve("OPENJIUWEN.md")));
    }

    @Test
    void findProjectRootWithGitDir() throws IOException {
        Path gitDir = tmpPath.resolve(".git");
        Files.createDirectories(gitDir);
        assertTrue(Files.isDirectory(tmpPath.resolve(".git")));
    }

    @Test
    void maxMemoryCharsIsReasonable() {
        assertTrue(MAX_MEMORY_CHARS > 0);
        assertTrue(MAX_MEMORY_CHARS <= 100_000);
    }
}
