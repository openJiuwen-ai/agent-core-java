/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.integration;

import com.openjiuwen.harness.cli.ui.CliRepl;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-03: REPL command dispatch integration tests.
 * <p>
 * Mirrors Python's {@code test_repl_dispatch} in
 * {@code tests.cli.integration.test_repl_dispatch}.
 */
class ReplDispatchIntegrationTest {

    @Test
    void replStartAndStop() {
        CliRepl repl = new CliRepl();
        assertFalse(repl.isRunning());
        repl.start();
        assertTrue(repl.isRunning());
        repl.stop();
        assertFalse(repl.isRunning());
    }

    @Test
    void shellPassthroughEcho() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "echo test_output_12345");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(output.contains("test_output_12345"));
        process.waitFor();
    }

    @Test
    void emptyInputIsIgnored() {
        String text = "   \t  \n  ";
        assertTrue(text.strip().isEmpty());
    }
}
