/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.prompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestBuildSystemPrompt} in
 * {@code tests/cli/unit/test_prompts.py}.
 */
class CliPromptBuilderPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void testContainsEnvironmentSection() {
        String prompt = CliPromptBuilder.buildSystemPrompt(
                tempDir.resolve("test").toString(),
                "gpt-4o",
                "OpenAI"
        );

        assertTrue(prompt.contains("Environment"));
    }

    @Test
    void testContainsCwd() {
        String cwd = tempDir.resolve("my").resolve("project").resolve("dir").toString();
        String prompt = CliPromptBuilder.buildSystemPrompt(
                cwd,
                "gpt-4o",
                "OpenAI"
        );

        assertTrue(prompt.contains(cwd));
    }

    @Test
    void testContainsModelName() {
        String prompt = CliPromptBuilder.buildSystemPrompt(
                tempDir.resolve("test").toString(),
                "qwen-max",
                "DashScope"
        );

        assertTrue(prompt.contains("qwen-max"));
        assertTrue(prompt.contains("DashScope"));
    }

    @Test
    void testContainsPlatformInfo() {
        String prompt = CliPromptBuilder.buildSystemPrompt(
                tempDir.resolve("test").toString(),
                "gpt-4o",
                "OpenAI"
        );

        assertTrue(prompt.contains("Platform"));
        assertTrue(prompt.contains("Python"));
    }

    @Test
    void testContainsDate() {
        String prompt = CliPromptBuilder.buildSystemPrompt(
                tempDir.resolve("test").toString(),
                "gpt-4o",
                "OpenAI"
        );

        assertTrue(prompt.contains("Date"));
    }
}
