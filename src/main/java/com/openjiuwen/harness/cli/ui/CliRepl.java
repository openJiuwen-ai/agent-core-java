/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

/**
 * CLI REPL (Read-Eval-Print Loop) for interactive mode.
 * <p>
 * Mirrors Python's {@code repl} in
 * {@code openjiuwen.harness.cli.ui.repl}.
 */
public class CliRepl {

    private volatile boolean running = false;

    /** Start the interactive REPL. */
    public void start() {
        running = true;
    }

    /** Stop the REPL. */
    public void stop() {
        running = false;
    }

    /** Check if REPL is running. */
    public boolean isRunning() {
        return running;
    }
}
