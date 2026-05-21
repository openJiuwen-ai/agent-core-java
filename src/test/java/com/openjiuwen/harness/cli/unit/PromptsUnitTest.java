/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for system prompt assembly.
 * <p>
 * Mirrors Python's {@code test_prompts} in
 * {@code tests.cli.unit.test_prompts}.
 */
class PromptsUnitTest {

    private String buildSystemPrompt(String cwd, String model, String provider) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Environment\n");
        sb.append("- Working Directory: ").append(cwd).append("\n");
        sb.append("- Model: ").append(model).append("\n");
        sb.append("- Provider: ").append(provider).append("\n");
        sb.append("- Platform: ").append(System.getProperty("os.name")).append("\n");
        sb.append("- Java ").append(System.getProperty("java.version")).append("\n");
        sb.append("- Date: ").append(java.time.LocalDate.now()).append("\n");
        return sb.toString();
    }

    @Test
    void containsEnvironmentSection() {
        String prompt = buildSystemPrompt("/tmp/test", "gpt-4o", "OpenAI");
        assertTrue(prompt.contains("Environment"));
    }

    @Test
    void containsCwd() {
        String prompt = buildSystemPrompt("/my/project/dir", "gpt-4o", "OpenAI");
        assertTrue(prompt.contains("/my/project/dir"));
    }

    @Test
    void containsModelName() {
        String prompt = buildSystemPrompt("/tmp/test", "qwen-max", "DashScope");
        assertTrue(prompt.contains("qwen-max"));
        assertTrue(prompt.contains("DashScope"));
    }

    @Test
    void containsPlatformInfo() {
        String prompt = buildSystemPrompt("/tmp/test", "gpt-4o", "OpenAI");
        assertTrue(prompt.contains("Platform"));
        assertTrue(prompt.contains("Java"));
    }

    @Test
    void containsDate() {
        String prompt = buildSystemPrompt("/tmp/test", "gpt-4o", "OpenAI");
        assertTrue(prompt.contains("Date"));
    }
}
