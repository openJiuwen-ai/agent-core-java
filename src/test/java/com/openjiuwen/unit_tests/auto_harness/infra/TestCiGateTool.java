/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.CIGateRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_ci_gate_tool}.
 */
class TestCiGateTool {

    @Test
    void normalizeMakeTestUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        String normalized = tool.normalizeCommand("make test");

        assertEquals(python + " -m pytest", normalized);
    }

    @Test
    void sanitizeFailureOutputKeepsOnlyPytestFailureSections() {
        String output = """
                ============================= test session starts ==============================
                tests/unit_tests/core/foundation/tool/test_api_param_mapper.py F         [100%]

                =================================== FAILURES ===================================
                E   AssertionError: expected value

                =============================== warnings summary ===============================
                tests/unit_tests/core/foundation/tool/test_api_param_mapper.py:60
                  PydanticDeprecatedSince20: `location` is deprecated

                -- Docs: https://docs.pytest.org/en/stable/how-to/capture-warnings.html
                - Generated html report: file:///tmp/report/index.html -
                =========================== short test summary info ============================
                FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x
                ========================= 1 failed, 2 warnings in 0.10s ========================
                """.strip();

        String sanitized = CIGateRunner.sanitizeFailureOutput(output);

        assertTrue(sanitized.contains("AssertionError"));
        assertTrue(sanitized.contains("short test summary info"));
        assertFalse(sanitized.contains("test session starts"));
        assertFalse(sanitized.contains("PydanticDeprecatedSince20"));
        assertFalse(sanitized.contains("warnings summary"));
    }

    @Test
    void runGateExecutesInstallCommandOnce(@TempDir Path tempDir) throws Exception {
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(0, "install ok");
        executor.enqueue(0, "gate ok");
        executor.enqueue(0, "gate ok");
        CIGateRunner tool = new CIGateRunner(
                tempDir.toString(),
                "",
                "",
                "uv sync --active --group dev --extra cli",
                executor);

        tool.runGate(Map.of("name", "test", "command", "echo ok"));
        tool.runGate(Map.of("name", "test", "command", "echo ok"));

        assertEquals(4, executor.commands.size());
        assertEquals(List.of("uv", "--version"), executor.commands.get(0));
        assertTrue(executor.commands.get(1).contains("uv sync --active --group dev --extra cli"));
    }

    private static Path fakePython(Path tempDir) throws IOException {
        Path python = tempDir.resolve("python3.11");
        Files.writeString(python, "");
        return python;
    }

    private static class FakeExecutor implements CIGateRunner.CommandExecutor {
        private final Queue<CIGateRunner.CommandResult> queued = new ArrayDeque<>();
        private final List<List<String>> commands = new ArrayList<>();

        void enqueue(int code, String output) {
            queued.add(new CIGateRunner.CommandResult(code, output));
        }

        @Override
        public CIGateRunner.CommandResult execute(List<String> command, String cwd, Map<String, String> env) {
            commands.add(List.copyOf(command));
            if (command.size() >= 2 && "uv".equals(command.get(0)) && "--version".equals(command.get(1))) {
                return new CIGateRunner.CommandResult(0, "uv 0.0");
            }
            if (queued.isEmpty()) {
                return new CIGateRunner.CommandResult(0, "ok");
            }
            return queued.remove();
        }
    }
}
