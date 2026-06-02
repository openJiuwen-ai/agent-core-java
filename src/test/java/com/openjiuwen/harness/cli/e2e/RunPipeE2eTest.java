/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-05: Pipe mode via stdin.
 * <p>
 * Mirrors Python's {@code test_run_pipe} in
 * {@code tests.cli.e2e.test_run_pipe}.
 */
class RunPipeE2eTest {

    private static final String PROMPT = "What is 3+3? Reply with just the number.";

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runPipeMode() throws Exception {
        CliResult result = runCli(PROMPT, "run", "-");

        assertEquals(0, result.returnCode());
        assertTrue(result.stdout().contains("6"));
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runAutoStdinDetection() throws Exception {
        CliResult result = runCli(PROMPT, "run");

        assertEquals(0, result.returnCode());
        assertTrue(result.stdout().contains("6"));
    }

    private static CliResult runCli(String stdin, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("openjiuwen.python.executable", "python"));
        command.add("-m");
        command.add("openjiuwen.harness.cli");
        command.addAll(List.of(args));

        Path pythonRoot = pythonProjectRoot();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(pythonRoot.toFile());
        builder.environment().putIfAbsent("OPENJIUWEN_API_BASE", "https://api.openai.com/v1");
        builder.environment().putIfAbsent("OPENJIUWEN_MODEL", "gpt-4o");
        builder.environment().putIfAbsent("OPENJIUWEN_PROVIDER", "OpenAI");
        builder.environment().put("PYTHONPATH", pythonRoot.toString());
        Process process = builder.start();
        process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CliResult(process.waitFor(), stdout, stderr);
    }

    private static Path pythonProjectRoot() {
        Path javaRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return javaRoot.getParent().resolve("agent-core-0.1.12").normalize();
    }

    private record CliResult(int returnCode, String stdout, String stderr) {
    }
}
