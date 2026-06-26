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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_ci_gate_tool} in
 * {@code tests/unit_tests/auto_harness/infra/test_ci_gate_tool.py}.
 */
class TestCiGateTool {

    @Test
    void loadsGatesFromYaml(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("ci_gate.yaml");
        Files.writeString(config, """
                ci_gates:
                  - name: lint
                    command: make check
                """);

        CIGateRunner tool = new CIGateRunner(tempDir.toString(), config.toString());

        assertEquals(1, tool.getGates().size());
        assertEquals("lint", tool.getGates().get(0).get("name"));
    }

    @Test
    void missingYamlReturnsEmpty(@TempDir Path tempDir) {
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), tempDir.resolve("missing.yaml").toString());

        assertEquals(List.of(), tool.getGates());
    }

    @Test
    void matchAll(@TempDir Path tempDir) {
        CIGateRunner tool = runnerWithStandardGates(tempDir);

        assertEquals(3, tool.matchGates("all").size());
    }

    @Test
    void matchCheckMapsToLint(@TempDir Path tempDir) {
        CIGateRunner tool = runnerWithStandardGates(tempDir);
        List<Map<String, Object>> matched = tool.matchGates("check");

        assertEquals(1, matched.size());
        assertEquals("lint", matched.get(0).get("name"));
    }

    @Test
    void matchTest(@TempDir Path tempDir) {
        CIGateRunner tool = runnerWithStandardGates(tempDir);
        List<Map<String, Object>> matched = tool.matchGates("test");

        assertEquals(1, matched.size());
        assertEquals("test", matched.get(0).get("name"));
    }

    @Test
    void matchUnknown(@TempDir Path tempDir) {
        CIGateRunner tool = runnerWithStandardGates(tempDir);

        assertEquals(List.of(), tool.matchGates("unknown"));
    }

    @Test
    void normalizeMakeTestUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        String normalized = tool.normalizeCommand("make test");

        assertEquals(python + " -m pytest", normalized);
    }

    @Test
    void normalizeMakeTestKeepsOriginalWhenMakeAvailable(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());
        setMakeAvailable(tool, true);

        assertEquals("make test", tool.normalizeCommand("make test"));
    }

    @Test
    void normalizeMakeTestPreservesFlags(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        String normalized = tool.normalizeCommand("make test TESTFLAGS=tests/unit_tests/harness/");

        assertEquals(python + " -m pytest tests/unit_tests/harness/", normalized);
    }

    @Test
    void normalizeShellPrefixedMakeTestUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        String normalized = tool.normalizeCommand("PATH=\"/tmp/bin:$PATH\" make test");

        assertEquals("PATH=\"/tmp/bin:$PATH\" " + python + " -m pytest", normalized);
    }

    @Test
    void normalizePythonModuleCommandUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        assertEquals(python + " -m pytest -q", tool.normalizeCommand("python -m pytest -q"));
    }

    @Test
    void resolvePythonExecutablePrefersConfiguredPath(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        assertEquals(python.toString(), tool.resolvePythonExecutable());
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
    void allPass(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(0, "All good");
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
        tool.setGates(List.of(Map.of("name", "lint", "command", "echo ok")));

        Map<String, Object> result = tool.run("all").join();

        assertEquals(true, result.get("passed"));
        assertEquals(1, ((List<?>) result.get("gates")).size());
        assertEquals("", result.get("errors"));
    }

    @Test
    void gateFails(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(1, "E501 line too long");
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
        tool.setGates(List.of(Map.of("name", "lint", "command", "echo bad")));

        Map<String, Object> result = tool.run("check").join();

        assertEquals(false, result.get("passed"));
        assertTrue(String.valueOf(result.get("errors")).contains("[lint]"));
        assertTrue(String.valueOf(result.get("errors")).contains("E501 line too long"));
    }

    @Test
    void noMatchingGate(@TempDir Path tempDir) {
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", new FakeExecutor());
        tool.setGates(List.of());

        Map<String, Object> result = tool.run("test").join();

        assertEquals(false, result.get("passed"));
        assertTrue(String.valueOf(result.get("errors")).contains("No gate matched"));
    }

    @Test
    void defaultActionIsAll(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(0, "ok");
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
        tool.setGates(List.of(Map.of("name", "lint", "command", "echo ok")));

        Map<String, Object> result = tool.run().join();

        assertEquals(true, result.get("passed"));
    }

    @Test
    void runGateRewritesMakeTest(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(0, "ok");
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", executor);

        Map<String, Object> result = tool.runGate(Map.of("name", "test", "command", "make test"));

        assertEquals(true, result.get("passed"));
        assertEquals(python + " -m pytest", executor.commands.get(0).get(2));
    }

    @Test
    void runGateRewritesShellPrefixedMakeTest(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(0, "ok");
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", executor);

        Map<String, Object> result = tool.runGate(
                Map.of("name", "test", "command", "PATH=\"/tmp/bin:$PATH\" make test"));

        assertEquals(true, result.get("passed"));
        assertEquals("PATH=\"/tmp/bin:$PATH\" " + python + " -m pytest", executor.commands.get(0).get(2));
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

    @Test
    void runGateFiltersWarningSummaryFromFailedOutput(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor();
        executor.enqueue(1, """
                =================================== FAILURES ===================================
                E   AssertionError: expected value

                =============================== warnings summary ===============================
                tests/unit_tests/core/foundation/tool/test_api_param_mapper.py:60
                  PydanticDeprecatedSince20: `location` is deprecated

                -- Docs: https://docs.pytest.org/en/stable/how-to/capture-warnings.html
                - Generated html report: file:///tmp/report/index.html -
                =========================== short test summary info ============================
                FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x
                """);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
        tool.setGates(List.of(Map.of("name", "test", "command", "make test")));

        Map<String, Object> result = tool.run("all").join();
        String errors = String.valueOf(result.get("errors"));

        assertEquals(false, result.get("passed"));
        assertTrue(errors.contains("AssertionError: expected value"));
        assertTrue(errors.contains("FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x"));
        assertFalse(errors.contains("PydanticDeprecatedSince20"));
    }

    @Test
    void commandEnvRemovesVirtualEnv(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        Map<String, String> env = tool.commandEnv();

        assertFalse(env.containsKey("VIRTUAL_ENV"));
        assertEquals(python.toString(), env.get("AUTO_HARNESS_PYTHON"));
        assertTrue(env.get("PATH").contains(python.getParent().toString()));
    }

    @Test
    void commandEnvIncludesCiFlag(@TempDir Path tempDir) {
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", new FakeExecutor());

        assertEquals("1", tool.commandEnv().get("CI"));
    }

    @Test
    void commandEnvPreservesExistingPath(@TempDir Path tempDir) throws Exception {
        Path python = fakePython(tempDir);
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

        Map<String, String> env = tool.commandEnv();
        String path = env.get("PATH");
        String originalPath = System.getenv().getOrDefault("PATH", System.getenv().getOrDefault("Path", ""));

        assertNotNull(path);
        assertTrue(path.contains(python.getParent().toString()));
        if (!originalPath.isBlank()) {
            assertTrue(path.contains(originalPath));
        }
    }

    private static Path fakePython(Path tempDir) throws IOException {
        Path python = tempDir.resolve("python3.11");
        Files.writeString(python, "");
        return python;
    }

    private static CIGateRunner runnerWithStandardGates(Path tempDir) {
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", new FakeExecutor());
        tool.setGates(List.of(
                Map.of("name", "lint", "command", "make check"),
                Map.of("name", "test", "command", "make test"),
                Map.of("name", "type-check", "command", "make type-check")));
        return tool;
    }

    private static void setMakeAvailable(CIGateRunner tool, boolean value) throws ReflectiveOperationException {
        java.lang.reflect.Field field = CIGateRunner.class.getDeclaredField("makeAvailable");
        field.setAccessible(true);
        field.setBoolean(tool, value);
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
