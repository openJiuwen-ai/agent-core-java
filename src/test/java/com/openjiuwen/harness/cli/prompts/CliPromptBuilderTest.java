/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.prompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliPromptBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildSystemPromptContainsEnvironmentSection() {
        String prompt = CliPromptBuilder.buildSystemPrompt(
                tempDir.toString(),
                "gpt-4o",
                "OpenAI"
        );

        assertTrue(prompt.contains("Environment"));
    }

    @Test
    void buildSystemPromptContainsDynamicEnvironmentFields() {
        String prompt = CliPromptBuilder.buildSystemPrompt(
                tempDir.resolve("workspace").toString(),
                "qwen-max",
                "DashScope"
        );

        assertTrue(prompt.contains("qwen-max"));
        assertTrue(prompt.contains("DashScope"));
        assertTrue(prompt.contains("Platform"));
        assertTrue(prompt.contains("Java"));
        assertTrue(prompt.contains("Date"));
    }

    @Test
    void buildSystemPromptContainsWorkingDirectory() {
        String cwd = tempDir.resolve("project").toString();
        String prompt = CliPromptBuilder.buildSystemPrompt(cwd, "gpt-4o", "OpenAI");

        assertTrue(prompt.contains(cwd));
    }

    @Test
    void loadOpenjiuwenMdLoadsProjectMemory() throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve("OPENJIUWEN.md"), "# Rules\n- Use pytest\n");

        String result = CliPromptBuilder.loadOpenjiuwenMd(tempDir.toString(), tempDir);

        assertNotNull(result);
        assertTrue(result.contains("Use pytest"));
    }

    @Test
    void loadOpenjiuwenMdLoadsUserMemory() throws Exception {
        Path userDir = tempDir.resolve(".openjiuwen");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("OPENJIUWEN.md"), "# Global\n- Always use English\n");

        String result = CliPromptBuilder.loadOpenjiuwenMd(tempDir.resolve("random").toString(), tempDir);

        assertNotNull(result);
        assertTrue(result.contains("Always use English"));
    }

    @Test
    void loadOpenjiuwenMdMergesUserAndProjectMemory() throws Exception {
        Path home = tempDir.resolve("home");
        Files.createDirectories(home.resolve(".openjiuwen"));
        Files.writeString(home.resolve(".openjiuwen").resolve("OPENJIUWEN.md"), "USER_MARKER");

        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve("OPENJIUWEN.md"), "PROJECT_MARKER");

        String result = CliPromptBuilder.loadOpenjiuwenMd(project.toString(), home);

        assertNotNull(result);
        assertTrue(result.contains("USER_MARKER"));
        assertTrue(result.contains("PROJECT_MARKER"));
    }

    @Test
    void loadOpenjiuwenMdTruncatesLargeMemory() throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve("OPENJIUWEN.md"), "x".repeat(50_000));

        String result = CliPromptBuilder.loadOpenjiuwenMd(tempDir.toString(), tempDir);

        assertNotNull(result);
        assertTrue(result.length() <= CliPromptBuilder.MAX_MEMORY_CHARS + 50);
        assertTrue(result.contains("[...truncated]"));
    }

    @Test
    void loadOpenjiuwenMdReturnsNullWhenNoMemoryExists() {
        String result = CliPromptBuilder.loadOpenjiuwenMd(tempDir.resolve("missing").toString(), tempDir);

        assertNull(result);
    }

    @Test
    void findProjectRootFindsGitMarker() throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));
        Path sub = Files.createDirectories(tempDir.resolve("src").resolve("pkg"));

        assertEquals(tempDir, CliPromptBuilder.findProjectRoot(sub.toString()).orElseThrow());
    }

    @Test
    void findProjectRootFindsPyprojectMarker() throws Exception {
        Files.createFile(tempDir.resolve("pyproject.toml"));
        Path sub = Files.createDirectory(tempDir.resolve("src"));

        assertEquals(tempDir, CliPromptBuilder.findProjectRoot(sub.toString()).orElseThrow());
    }

    @Test
    void findProjectRootReturnsEmptyWhenNoMarkerExists() {
        assertTrue(CliPromptBuilder.findProjectRoot(tempDir.resolve("tmp").toString()).isEmpty());
    }
}
