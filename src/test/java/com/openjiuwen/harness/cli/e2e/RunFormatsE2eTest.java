/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-03 / E2E-04: output-format json and stream-json.
 * <p>
 * Mirrors Python's {@code test_run_formats} in
 * {@code tests.cli.e2e.test_run_formats}.
 */
class RunFormatsE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VALID_TYPES = Set.of(
            "llm_output", "llm_reasoning", "answer", "message",
            "__interaction__", "controller_output");

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runJsonFormat() throws Exception {
        CliResult result = runCli("run", "-f", "json", "What is 2+2?");

        assertEquals(0, result.returnCode());
        JsonNode data = MAPPER.readTree(result.stdout());
        assertTrue(data.has("result"));
        assertFalse(data.get("result").asText().isBlank());
        assertTrue(data.get("chunks").isInt());
        assertTrue(data.get("chunks").asInt() > 0);
        assertTrue(data.has("model"));
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runStreamJsonFormat() throws Exception {
        CliResult result = runCli("run", "-f", "stream-json", "Say hello");

        assertEquals(0, result.returnCode());
        List<String> lines = result.stdout().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertTrue(lines.size() >= 1);

        boolean hasContent = false;
        for (String line : lines) {
            JsonNode data = MAPPER.readTree(line);
            assertTrue(data.has("type"));
            assertTrue(data.has("index"));
            assertTrue(VALID_TYPES.contains(data.get("type").asText()));
            if ("llm_output".equals(data.get("type").asText()) || "answer".equals(data.get("type").asText())) {
                hasContent = true;
            }
        }
        assertTrue(hasContent);
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
