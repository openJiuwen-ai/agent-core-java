/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.prompts.CliPromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OPENJIUWEN.md memory loading.
 * <p>
 * Mirrors Python's {@code test_memory} in
 * {@code tests.cli.unit.test_memory}.
 */
class MemoryUnitTest {

    @Test
    void loadProjectMemory(@TempDir Path tempDir) throws IOException {
        Path home = tempDir.resolve("home");
        Path project = tempDir.resolve("project");
        Files.createDirectories(home);
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve("OPENJIUWEN.md"), "# Rules\n- Use pytest\n");

        String result = CliPromptBuilder.loadOpenjiuwenMd(project.toString(), home);

        assertNotNull(result);
        assertTrue(result.contains("Use pytest"));
    }

    @Test
    void loadUserMemory(@TempDir Path tempDir) throws IOException {
        Path home = tempDir.resolve("home");
        Path userDir = home.resolve(".openjiuwen");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("OPENJIUWEN.md"), "# Global\n- Always use English\n");

        String result = CliPromptBuilder.loadOpenjiuwenMd(tempDir.resolve("random").toString(), home);

        assertNotNull(result);
        assertTrue(result.contains("Always use English"));
    }

    @Test
    void projectAndUserMerged(@TempDir Path tempDir) throws IOException {
        Path home = tempDir.resolve("home");
        Path userDir = home.resolve(".openjiuwen");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("OPENJIUWEN.md"), "USER_MARKER");

        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve("OPENJIUWEN.md"), "PROJECT_MARKER");

        String result = CliPromptBuilder.loadOpenjiuwenMd(project.toString(), home);

        assertNotNull(result);
        assertTrue(result.contains("USER_MARKER"));
        assertTrue(result.contains("PROJECT_MARKER"));
    }

    @Test
    void truncation(@TempDir Path tempDir) throws IOException {
        Path home = tempDir.resolve("home");
        Path project = tempDir.resolve("project");
        Files.createDirectories(home);
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve("OPENJIUWEN.md"), "x".repeat(50_000));

        String result = CliPromptBuilder.loadOpenjiuwenMd(project.toString(), home);

        assertNotNull(result);
        assertTrue(result.length() <= CliPromptBuilder.maxMemoryChars() + 50);
        assertTrue(result.contains("[...truncated]"));
    }

    @Test
    void noMemoryReturnsNone(@TempDir Path tempDir) throws IOException {
        Path home = tempDir.resolve("home");
        Path empty = tempDir.resolve("empty");
        Files.createDirectories(home);
        Files.createDirectories(empty);

        assertNull(CliPromptBuilder.loadOpenjiuwenMd(empty.toString(), home));
    }

    @Test
    void findByGit(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve(".git"));
        Path sub = tempDir.resolve("src").resolve("pkg");
        Files.createDirectories(sub);

        Optional<Path> root = CliPromptBuilder.findProjectRoot(sub.toString());

        assertTrue(root.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), root.get());
    }

    @Test
    void findByPyproject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("pyproject.toml"), "");
        Path sub = tempDir.resolve("src");
        Files.createDirectories(sub);

        Optional<Path> root = CliPromptBuilder.findProjectRoot(sub.toString());

        assertTrue(root.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), root.get());
    }

    @Test
    void noRootFound(@TempDir Path tempDir) throws IOException {
        Path sub = tempDir.resolve("src");
        Files.createDirectories(sub);

        assertTrue(CliPromptBuilder.findProjectRoot(sub.toString()).isEmpty());
    }
}
