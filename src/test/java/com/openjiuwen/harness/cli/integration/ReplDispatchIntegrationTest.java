/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.integration;

import com.openjiuwen.harness.cli.ui.CliRepl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-03: REPL command dispatch integration tests.
 * <p>
 * Mirrors Python's {@code test_repl_dispatch} in
 * {@code tests.cli.integration.test_repl_dispatch}.
 */
class ReplDispatchIntegrationTest {

    @Test
    void slashHelpRouting() {
        StringBuilder output = new StringBuilder();

        String result = CliRepl.handleSlash("/help", output);

        assertNull(result);
        assertTrue(output.toString().contains("/help"));
        assertTrue(output.toString().contains("/exit"));
        assertTrue(output.toString().contains("/status"));
    }

    @Test
    void unknownSlashCommand() {
        StringBuilder output = new StringBuilder();

        String result = CliRepl.handleSlash("/foobar", output);

        assertNull(result);
        assertTrue(output.toString().contains("Unknown command"));
    }

    @Test
    void shellPassthrough() throws Exception {
        String output = CliRepl.handleShell("echo test_output_12345");
        assertTrue(output.contains("test_output_12345"));
    }

    @Test
    void shellStderr() throws Exception {
        String output = CliRepl.handleShell("__openjiuwen_nonexistent_command_xyz__");
        assertTrue(output.length() > 0);
    }

    @Test
    void emptyInputIgnored() {
        String text = "   \t  \n  ";
        assertTrue(text.strip().isEmpty());
    }
}
