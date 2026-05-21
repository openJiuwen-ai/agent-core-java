/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_ci_gate_tool}.
 * Tests for CIGateRunner YAML loading and gate matching functionality.
 */
class CIGateRunnerTest {

    @Test
    void loadsGatesFromYaml() throws Exception {
        Path file = Files.createTempFile("ci-gate", ".yaml");
        Files.writeString(file, "ci_gates:\n  - name: lint\n    command: make check\n");
        try {
            CIGateRunner runner = new CIGateRunner("/tmp", file.toString(), "", "");
            assertEquals(1, runner.getGates().size());
            assertEquals("lint", runner.getGates().get(0).get("name"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingYamlReturnsEmpty() {
        CIGateRunner runner = new CIGateRunner("/tmp", "/nonexistent.yaml", "", "");
        assertTrue(runner.getGates().isEmpty());
    }

    @Test
    void matchCheckMapsToLint() throws Exception {
        Path file = Files.createTempFile("ci-gate", ".yaml");
        Files.writeString(file, "ci_gates:\n  - name: lint\n    command: make check\n  - name: test\n    command: make test\n");
        try {
            CIGateRunner runner = new CIGateRunner("/tmp", file.toString(), "", "");
            assertEquals(1, runner.matchGates("check").size());
            assertEquals("lint", runner.matchGates("check").get(0).get("name"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
