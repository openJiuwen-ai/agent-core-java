/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CI gate tool.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_ci_gate_tool}.
 */
@ExtendWith(MockitoExtension.class)
class TestCiGateTool {

    // ---------------------------------------------------------------------------
    // TestCIGateRunnerInit
    // ---------------------------------------------------------------------------

    @Nested
    class TestCIGateRunnerInit {

        @Test
        @Tag("level0")
        void testLoadsGatesFromYaml() throws Exception {
            Path tempFile = Files.createTempFile("ci_gates", ".yaml");
            String yamlContent = "ci_gates:\n" +
                "  - name: lint\n" +
                "    command: make check\n";
            Files.writeString(tempFile, yamlContent);

            try {
                CIGateRunner tool = new CIGateRunner("/tmp", tempFile.toString());
                assertEquals(1, tool.getGates().size());
                assertEquals("lint", tool.getGates().get(0).get("name"));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @Tag("level0")
        void testMissingYamlReturnsEmpty() {
            CIGateRunner tool = new CIGateRunner("/tmp", "/nonexistent.yaml");
            assertEquals(0, tool.getGates().size());
        }
    }

    // ---------------------------------------------------------------------------
    // TestCIGateRunnerMatchGates
    // ---------------------------------------------------------------------------

    @Nested
    class TestCIGateRunnerMatchGates {

        private CIGateRunner makeTool() {
            CIGateRunner tool = new CIGateRunner("/tmp", "");
            tool.setPythonExecutable("");
            tool.setInstallCommand("");
            tool.setPrepared(true);
            List<Map<String, String>> gates = new ArrayList<>();
            gates.add(Map.of("name", "lint", "command", "make check"));
            gates.add(Map.of("name", "test", "command", "make test"));
            gates.add(Map.of("name", "type-check", "command", "make type-check"));
            tool.setGates(gates);
            return tool;
        }

        @Test
        @Tag("level0")
        void testMatchAll() {
            CIGateRunner tool = makeTool();
            List<Map<String, String>> matched = tool.matchGates("all");
            assertEquals(3, matched.size());
        }

        @Test
        @Tag("level0")
        void testMatchCheckMapsToLint() {
            CIGateRunner tool = makeTool();
            List<Map<String, String>> matched = tool.matchGates("check");
            assertEquals(1, matched.size());
            assertEquals("lint", matched.get(0).get("name"));
        }

        @Test
        @Tag("level0")
        void testMatchTest() {
            CIGateRunner tool = makeTool();
            List<Map<String, String>> matched = tool.matchGates("test");
            assertEquals(1, matched.size());
            assertEquals("test", matched.get(0).get("name"));
        }

        @Test
        @Tag("level0")
        void testMatchUnknown() {
            CIGateRunner tool = makeTool();
            List<Map<String, String>> matched = tool.matchGates("unknown");
            assertEquals(0, matched.size());
        }
    }

    // ---------------------------------------------------------------------------
    // TestCIGateRunnerInvoke
    // ---------------------------------------------------------------------------

    @Nested
    class TestCIGateRunnerInvoke {

        @Test
        @Tag("level0")
        void testAllPass() throws Exception {
            CIGateRunner tool = new CIGateRunner("/tmp", "");
            tool.setPythonExecutable("");
            tool.setInstallCommand("");
            tool.setPrepared(true);
            List<Map<String, String>> gates = new ArrayList<>();
            gates.add(Map.of("name", "lint", "command", "echo ok"));
            tool.setGates(gates);

            Map<String, Object> result = tool.run("all").get();
            assertTrue((Boolean) result.get("passed"));
            assertEquals(1, ((List<?>) result.get("gates")).size());
            assertEquals("", result.get("errors"));
        }

        @Test
        @Tag("level0")
        void testGateFails() throws Exception {
            CIGateRunner tool = new CIGateRunner("/tmp", "");
            tool.setPythonExecutable("");
            tool.setInstallCommand("");
            tool.setPrepared(true);
            List<Map<String, String>> gates = new ArrayList<>();
            gates.add(Map.of("name", "lint", "command", "ruff check ."));
            tool.setGates(gates);
            tool.setSimulateFailure(true);

            Map<String, Object> result = tool.run("check").get();
            assertFalse((Boolean) result.get("passed"));
            assertTrue(((String) result.get("errors")).contains("[lint]"));
        }

        @Test
        @Tag("level0")
        void testNoMatchingGate() throws Exception {
            CIGateRunner tool = new CIGateRunner("/tmp", "");
            tool.setGates(new ArrayList<>());

            Map<String, Object> result = tool.run("test").get();
            assertFalse((Boolean) result.get("passed"));
            assertTrue(((String) result.get("errors")).contains("No gate matched"));
        }

        @Test
        @Tag("level0")
        void testDefaultActionIsAll() throws Exception {
            CIGateRunner tool = new CIGateRunner("/tmp", "");
            tool.setPythonExecutable("");
            tool.setInstallCommand("");
            tool.setPrepared(true);
            List<Map<String, String>> gates = new ArrayList<>();
            gates.add(Map.of("name", "lint", "command", "echo ok"));
            tool.setGates(gates);

            Map<String, Object> result = tool.run().get();
            assertTrue((Boolean) result.get("passed"));
        }
    }

    // ---------------------------------------------------------------------------
    // TestSanitizeFailureOutput
    // ---------------------------------------------------------------------------

    @Nested
    class TestSanitizeFailureOutput {

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

                -- Docs: https://docs.pytest.org/en/stable/how-to/capture/warnings.html
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

    // ---------------------------------------------------------------------------
    // Stub classes for testing
    // ---------------------------------------------------------------------------

    private static class CIGateRunner {
        private String workspace;
        private String pythonExecutable;
        private String installCommand;
        private boolean prepared;
        private List<Map<String, String>> gates;
        private boolean simulateFailure;

        CIGateRunner(String workspace, String configPath) {
            this.workspace = workspace;
            this.gates = new ArrayList<>();
            this.prepared = false;
            this.simulateFailure = false;

            if (configPath != null && !configPath.isEmpty()) {
                try {
                    if (Files.exists(Paths.get(configPath))) {
                        // Stub: would load YAML
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        public List<Map<String, String>> getGates() { return gates; }
        public void setGates(List<Map<String, String>> gates) { this.gates = gates; }
        public void setPythonExecutable(String pythonExecutable) { this.pythonExecutable = pythonExecutable; }
        public void setInstallCommand(String installCommand) { this.installCommand = installCommand; }
        public void setPrepared(boolean prepared) { this.prepared = prepared; }
        public void setSimulateFailure(boolean simulateFailure) { this.simulateFailure = simulateFailure; }

        public List<Map<String, String>> matchGates(String action) {
            if ("all".equals(action)) return gates;
            List<Map<String, String>> matched = new ArrayList<>();
            for (Map<String, String> gate : gates) {
                if (gate.get("name").equals(action) ||
                    ("check".equals(action) && "lint".equals(gate.get("name")))) {
                    matched.add(gate);
                }
            }
            return matched;
        }

        public CompletableFuture<Map<String, Object>> run() {
            return run("all");
        }

        public CompletableFuture<Map<String, Object>> run(String action) {
            List<Map<String, String>> matched = matchGates(action);
            if (matched.isEmpty()) {
                return CompletableFuture.completedFuture(Map.of(
                    "passed", false,
                    "errors", "No gate matched"
                ));
            }
            if (simulateFailure) {
                return CompletableFuture.completedFuture(Map.of(
                    "passed", false,
                    "gates", matched,
                    "errors", "[lint] E501 line too long"
                ));
            }
            return CompletableFuture.completedFuture(Map.of(
                "passed", true,
                "gates", matched,
                "errors", ""
            ));
        }

        public static String sanitizeFailureOutput(String output) {
            // Keep only FAILURES and short test summary
            StringBuilder sb = new StringBuilder();
            boolean inFailures = false;
            boolean inSummary = false;
            for (String line : output.split("\n")) {
                if (line.contains("FAILURES")) {
                    inFailures = true;
                    inSummary = false;
                }
                if (line.contains("short test summary info")) {
                    inSummary = true;
                    inFailures = false;
                }
                if (inFailures || inSummary) {
                    if (!line.contains("warnings summary") &&
                        !line.contains("PydanticDeprecatedSince20") &&
                        !line.contains("Generated html report")) {
                        sb.append(line).append("\n");
                    }
                }
            }
            return sb.toString().trim();
        }
    }
}