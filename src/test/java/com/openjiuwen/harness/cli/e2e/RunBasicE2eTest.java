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
 * E2E-02: Basic non-interactive run.
 * <p>
 * Mirrors Python's {@code test_run_basic} in
 * {@code tests.cli.e2e.test_run_basic}.
 */
class RunBasicE2eTest {

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runBasic() throws Exception {
        CliResult result = runCli(
                "run",
                "What is 2+2? Reply with just the number.");

        assertEquals(0, result.returnCode());
        assertTrue(result.stdout().contains("4"));
        assertFalse(result.stderr().contains("Traceback"));
    }

    private static CliResult runCli(String... args) throws Exception {
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
