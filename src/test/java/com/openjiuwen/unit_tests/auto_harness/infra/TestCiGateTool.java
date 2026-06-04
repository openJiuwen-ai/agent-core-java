/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.CIGateRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CI gate tool.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_ci_gate_tool}.
 */
@ExtendWith(MockitoExtension.class)
class TestCiGateTool {

    @Nested
    class TestCIGateRunnerInit {

        @Test
        @Tag("level0")
        void testLoadsGatesFromYaml(@TempDir Path tempDir) throws Exception {
            Path config = tempDir.resolve("ci_gates.yaml");
            Files.writeString(config, "ci_gates:\n  - name: lint\n    command: make check\n");

            CIGateRunner tool = new CIGateRunner(tempDir.toString(), config.toString());

            assertEquals(1, tool.getGates().size());
            assertEquals("lint", tool.getGates().get(0).get("name"));
        }

        @Test
        @Tag("level0")
        void testMissingYamlReturnsEmpty(@TempDir Path tempDir) {
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), tempDir.resolve("missing.yaml").toString());

            assertEquals(0, tool.getGates().size());
        }
    }

    @Nested
    class TestCIGateRunnerMatchGates {

        @Test
        @Tag("level0")
        void testMatchAll(@TempDir Path tempDir) {
            CIGateRunner tool = makeTool(tempDir, new FakeExecutor());

            List<Map<String, Object>> matched = tool.matchGates("all");

            assertEquals(3, matched.size());
        }

        @Test
        @Tag("level0")
        void testMatchCheckMapsToLint(@TempDir Path tempDir) {
            CIGateRunner tool = makeTool(tempDir, new FakeExecutor());

            List<Map<String, Object>> matched = tool.matchGates("check");

            assertEquals(1, matched.size());
            assertEquals("lint", matched.get(0).get("name"));
        }

        @Test
        @Tag("level0")
        void testMatchTest(@TempDir Path tempDir) {
            CIGateRunner tool = makeTool(tempDir, new FakeExecutor());

            List<Map<String, Object>> matched = tool.matchGates("test");

            assertEquals(1, matched.size());
            assertEquals("test", matched.get(0).get("name"));
        }

        @Test
        @Tag("level0")
        void testMatchUnknown(@TempDir Path tempDir) {
            CIGateRunner tool = makeTool(tempDir, new FakeExecutor());

            List<Map<String, Object>> matched = tool.matchGates("unknown");

            assertEquals(0, matched.size());
        }

        @Test
        @Tag("level0")
        void testNormalizeMakeTestUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

            String normalized = tool.normalizeCommand("make test");

            assertEquals(python + " -m pytest", normalized);
        }

        @Test
        @Tag("level0")
        void testNormalizeMakeTestPreservesFlags(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

            String normalized = tool.normalizeCommand("make test TESTFLAGS=tests/unit_tests/harness/");

            assertEquals(python + " -m pytest tests/unit_tests/harness/", normalized);
        }

        @Test
        @Tag("level0")
        void testNormalizeShellPrefixedMakeTestUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

            String normalized = tool.normalizeCommand("PATH=\"/tmp/bin:$PATH\" make test");

            assertEquals("PATH=\"/tmp/bin:$PATH\" " + python + " -m pytest", normalized);
        }

        @Test
        @Tag("level0")
        void testNormalizePythonModuleCommandUsesConfiguredPython(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

            String normalized = tool.normalizeCommand("python -m pytest -q");

            assertEquals(python + " -m pytest -q", normalized);
        }

        @Test
        @Tag("level0")
        void testResolvePythonExecutablePrefersConfiguredPath(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", new FakeExecutor());

            assertEquals(python.toString(), tool.resolvePythonExecutable());
        }

        @Test
        @Tag("level1")
        void testSanitizeFailureOutputKeepsOnlyPytestFailureSections() {
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
            assertFalse(sanitized.contains("Generated html report"));
        }
    }

    @Nested
    class TestCIGateRunnerInvoke {

        @Test
        @Tag("level0")
        void testAllPass(@TempDir Path tempDir) throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, "All good");
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
            tool.setPrepared(true);
            tool.setGates(List.of(Map.of("name", "lint", "command", "echo ok")));

            Map<String, Object> result = tool.run("all").get();

            assertTrue((Boolean) result.get("passed"));
            assertEquals(1, gates(result).size());
            assertEquals("", result.get("errors"));
        }

        @Test
        @Tag("level0")
        void testGateFails(@TempDir Path tempDir) throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(1, "E501 line too long");
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
            tool.setPrepared(true);
            tool.setGates(List.of(Map.of("name", "lint", "command", "ruff check .")));

            Map<String, Object> result = tool.run("check").get();

            assertFalse((Boolean) result.get("passed"));
            assertTrue(((String) result.get("errors")).contains("[lint]"));
            assertTrue(((String) result.get("errors")).contains("E501 line too long"));
        }

        @Test
        @Tag("level0")
        void testNoMatchingGate(@TempDir Path tempDir) throws Exception {
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", new FakeExecutor());
            tool.setGates(List.of());

            Map<String, Object> result = tool.run("test").get();

            assertFalse((Boolean) result.get("passed"));
            assertTrue(((String) result.get("errors")).contains("No gate matched"));
        }

        @Test
        @Tag("level0")
        void testDefaultActionIsAll(@TempDir Path tempDir) throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, "ok");
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
            tool.setPrepared(true);
            tool.setGates(List.of(Map.of("name", "lint", "command", "echo ok")));

            Map<String, Object> result = tool.run().get();

            assertTrue((Boolean) result.get("passed"));
        }

        @Test
        @Tag("level0")
        void testRunGateRewritesMakeTest(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, "ok");
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", executor);
            tool.setPrepared(true);

            Map<String, Object> result = tool.runGate(Map.of("name", "test", "command", "make test"));

            assertTrue((Boolean) result.get("passed"));
            assertEquals(List.of("bash", "-c"), executor.commands.get(0).subList(0, 2));
            assertEquals(python + " -m pytest", executor.commands.get(0).get(2));
        }

        @Test
        @Tag("level0")
        void testRunGateRewritesShellPrefixedMakeTest(@TempDir Path tempDir) throws Exception {
            Path python = fakePython(tempDir);
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, "ok");
            CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", python.toString(), "", executor);
            tool.setPrepared(true);

            Map<String, Object> result = tool.runGate(
                    Map.of("name", "test", "command", "PATH=\"/tmp/bin:$PATH\" make test"));

            assertTrue((Boolean) result.get("passed"));
            assertEquals("PATH=\"/tmp/bin:$PATH\" " + python + " -m pytest", executor.commands.get(0).get(2));
        }

        @Test
        @Tag("level0")
        void testRunGateExecutesInstallCommandOnce(@TempDir Path tempDir) throws Exception {
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
            tool.runGate(Map.of("name", "lint", "command", "echo ok"));

            assertEquals(3, executor.commands.size());
            assertEquals("uv sync --active --group dev --extra cli", executor.commands.get(0).get(2));
        }

        @Test
        @Tag("level0")
        void testRunGateFiltersWarningSummaryFromFailedOutput(@TempDir Path tempDir) throws Exception {
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
            tool.setPrepared(true);
            tool.setGates(List.of(Map.of("name", "test", "command", "make test")));

            Map<String, Object> result = tool.run("all").get();

            assertFalse((Boolean) result.get("passed"));
            assertTrue(((String) result.get("errors")).contains("AssertionError: expected value"));
            assertTrue(((String) result.get("errors"))
                    .contains("FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x"));
            assertFalse(((String) result.get("errors")).contains("PydanticDeprecatedSince20"));
        }
    }

    private static CIGateRunner makeTool(Path tempDir, FakeExecutor executor) {
        CIGateRunner tool = new CIGateRunner(tempDir.toString(), "", "", "", executor);
        tool.setPythonExecutable("");
        tool.setInstallCommand("");
        tool.setPrepared(true);
        tool.setGates(List.of(
                Map.of("name", "lint", "command", "make check"),
                Map.of("name", "test", "command", "make test"),
                Map.of("name", "type-check", "command", "make type-check")));
        return tool;
    }

    private static Path fakePython(Path tempDir) throws IOException {
        Path python = tempDir.resolve("python3.11");
        Files.writeString(python, "");
        return python;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> gates(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("gates");
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
            if (queued.isEmpty()) {
                return new CIGateRunner.CommandResult(0, "ok");
            }
            return queued.remove();
        }
    }
}
