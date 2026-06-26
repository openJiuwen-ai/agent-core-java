/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import java.io.PrintStream;

/**
 * Runtime objects passed to Team CLI slash-command handlers.
 *
 * <p>Mirrors Python's command handlers receiving {@code TeamCli} in
 * {@code openjiuwen/agent_teams/cli/commands.py}.</p>
 */
public record CommandContext(
        TeamCliState state,
        Object inboxCallback,
        PrintStream terminal,
        PrintStream console
) {

    public CommandContext(TeamCliState state) {
        this(state, null, System.out, resolveConsole(state));
    }

    public CommandContext {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        terminal = terminal == null ? System.out : terminal;
        console = console == null ? resolveConsole(state) : console;
    }

    private static PrintStream resolveConsole(TeamCliState state) {
        if (state != null && state.getConsole() instanceof PrintStream printStream) {
            return printStream;
        }
        return System.out;
    }
}
