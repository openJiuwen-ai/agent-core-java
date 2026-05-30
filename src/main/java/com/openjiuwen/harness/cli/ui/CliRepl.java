/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.openjiuwen.harness.cli.AutoHarnessCliSupport;
import com.openjiuwen.harness.cli.AutoHarnessRunRequest;

import java.nio.file.Path;
import java.util.List;

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

    public AutoHarnessCliSupport.PreparedRun subcmdRun(List<String> args, String workspace, Path currentDirectory) {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        List<String> safeArgs = args != null ? args : List.of();
        for (int index = 0; index < safeArgs.size(); index++) {
            String arg = safeArgs.get(index);
            if ("--goal".equals(arg) && index + 1 < safeArgs.size()) {
                request.setGoal(safeArgs.get(++index));
            } else if ("--stage".equals(arg) && index + 1 < safeArgs.size()) {
                request.setStage(safeArgs.get(++index));
            } else if ("--task".equals(arg) && index + 1 < safeArgs.size()) {
                request.setTask(safeArgs.get(++index));
            }
        }
        return AutoHarnessCliSupport.prepareRun(options(workspace), request, currentDirectory);
    }

    public AutoHarnessCliSupport.PreparedRun cmdAutoHarness(String command, String workspace, Path currentDirectory) {
        String text = command == null ? "" : command.strip();
        if (text.startsWith("/auto-harness")) {
            text = text.substring("/auto-harness".length()).strip();
        }
        if (text.startsWith("run ")) {
            return subcmdRun(List.of("--goal", text.substring("run ".length()).strip()), workspace, currentDirectory);
        }
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setGoal(text);
        return AutoHarnessCliSupport.prepareRun(options(workspace), request, currentDirectory);
    }

    private static AutoHarnessCliSupport.CliOptions options(String workspace) {
        AutoHarnessCliSupport.CliOptions opts = new AutoHarnessCliSupport.CliOptions();
        opts.setWorkspace(workspace != null ? workspace : "");
        return opts;
    }
}
