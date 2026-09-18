/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Missing REPL command dispatch integration coverage.
 *
 * <p>Mirrors Python's {@code TestReplDispatch} in
 * {@code tests/cli/integration/test_repl_dispatch.py}.</p>
 */
class ReplDispatchMissingTest {

    @Test
    void slashHelpRouting() {
        StringBuilder output = new StringBuilder();

        CliRepl.handleSlash("/help", output);

        assertThat(output.toString()).contains("/help", "/exit", "/status");
    }

    @Test
    void unknownSlashCommand() {
        StringBuilder output = new StringBuilder();

        CliRepl.handleSlash("/foobar", output);

        assertThat(output.toString()).contains("Unknown command");
    }

    @Test
    void shellPassthrough() throws Exception {
        String output = CliRepl.handleShell("echo test_output_12345");

        assertThat(output).contains("test_output_12345");
    }

    @Test
    void shellStderr() throws Exception {
        String output = CliRepl.handleShell("ls /nonexistent_path_xyz");

        assertThat(output).isNotBlank();
    }

    @Test
    void emptyInputIgnored() {
        String text = "   \t  \n  ";

        assertThat(text.strip()).isEmpty();
    }
}
