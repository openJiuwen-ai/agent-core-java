/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

/**
 * Sentinel thrown by {@code /exit} and {@code /quit} to break the CLI loop.
 *
 * <p>Mirrors Python's {@code _ExitCli} in
 * {@code openjiuwen/agent_teams/cli/commands.py}.</p>
 */
public class ExitCli extends RuntimeException {

    public ExitCli() {
        super("exit cli");
    }
}
