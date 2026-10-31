/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.harness.cli.ui.CliRepl;
import com.openjiuwen.harness.cli.ui.CliRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Module entry point for the Harness CLI.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.__main__} in
 * {@code openjiuwen/harness/cli/__main__.py}.</p>
 */
public final class HarnessCliMain {

    public static final String PYTHON_MODULE = "openjiuwen/harness/cli/__main__.py";

    private HarnessCliMain() {
    }

    public static void main(String[] args) {
        int exitCode = runSafely(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Run the CLI, reporting a usage error the way Click does instead of letting it escape as an
     * unhandled exception. Usage errors exit with 2 (Click's {@code UsageError.exit_code}).
     */
    static int runSafely(String[] args) {
        try {
            return run(args, System.console() != null, HarnessCliMain::readStdin, new CliRunner());
        } catch (IllegalArgumentException error) {
            System.err.println("Error: " + error.getMessage());
            return 2;
        }
    }

    static int run(String[] args, boolean stdinIsTty, Supplier<String> stdinReader, CliRunner runner) {
        List<String> tokens = args == null ? List.of() : new ArrayList<>(Arrays.asList(args));
        String command;
        List<String> rest;
        if (tokens.isEmpty()) {
            command = HarnessCli.defaultCommand(stdinIsTty);
            rest = List.of();
        } else {
            command = tokens.get(0);
            rest = tokens.subList(1, tokens.size());
        }

        if (HarnessCli.COMMAND_RUN.equals(command)) {
            RunInvocation invocation = parseRunInvocation(rest);
            String prompt = HarnessCli.resolveRunPrompt(invocation.prompt(), stdinIsTty, stdinReader);
            return HarnessCli.runOnce(new CLIOptions(), prompt, invocation.outputFormat(), runner);
        }
        if (HarnessCli.COMMAND_CHAT.equals(command)) {
            CliRepl repl = new CliRepl();
            repl.start();
            repl.stop();
            return 0;
        }
        if (!stdinIsTty && tokens.isEmpty()) {
            String prompt = HarnessCli.resolveRunPrompt(null, false, stdinReader);
            return HarnessCli.runOnce(new CLIOptions(), prompt, CliRunner.OUTPUT_TEXT, runner);
        }
        throw new IllegalArgumentException("Unknown command: " + command);
    }

    private static RunInvocation parseRunInvocation(List<String> args) {
        String outputFormat = CliRunner.OUTPUT_TEXT;
        String prompt = null;
        List<String> safeArgs = args == null ? List.of() : args;
        for (int index = 0; index < safeArgs.size(); index++) {
            String arg = safeArgs.get(index);
            if ("--output-format".equals(arg) || "-f".equals(arg)) {
                if (index + 1 >= safeArgs.size()) {
                    throw new IllegalArgumentException("Option '--output-format' requires a value");
                }
                String value = safeArgs.get(++index);
                if (!CliRunner.OUTPUT_TEXT.equals(value)
                        && !CliRunner.OUTPUT_JSON.equals(value)
                        && !CliRunner.OUTPUT_STREAM_JSON.equals(value)) {
                    throw new IllegalArgumentException(
                            "Invalid value for '--output-format': '" + value
                                    + "' is not one of 'text', 'json', 'stream-json'");
                }
                outputFormat = value;
            } else if (prompt == null) {
                prompt = arg;
            } else {
                prompt = prompt + " " + arg;
            }
        }
        return new RunInvocation(prompt, outputFormat);
    }

    private static String readStdin() {
        try {
            byte[] input = System.in.readAllBytes();
            return new String(input, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private record RunInvocation(String prompt, String outputFormat) {
    }
}
