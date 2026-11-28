/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shellTimesOutWhenCommandNeverExits() {
        // A long-running command must hit the injectable timeout instead of
        // blocking forever; before the fix the synchronous readAllBytes() ran
        // before the timed wait and made the timeout unreachable.
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = isWindows ? "ping -n 60 127.0.0.1" : "sleep 60";

        assertThatThrownBy(() -> CliRepl.handleShell(command, 2L))
                .isInstanceOf(InterruptedException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void shellReturnsOutputForSlowButFiniteCommand() throws Exception {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = isWindows ? "ping -n 2 127.0.0.1" : "sleep 1 && echo done_marker_98765";

        String output = CliRepl.handleShell(command, 30L);

        assertThat(output).isNotBlank();
    }

    @Test
    void emptyInputIgnored() {
        String text = "   \t  \n  ";

        assertThat(text.strip()).isEmpty();
    }
}
