/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.constants.TimeoutConstants;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.SysOperationError;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Minimal local code execution tool.
 * 
 * @since 0.1.7
 */
public class CodeTool {
    /**
     * invoke.
     * 
     * @param code code
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(String code, String language) {
        if (code == null) {
            return ToolOutput.builder().success(false).error("code is required").build();
        }
        String normalized = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (!List.of("python", "python3", "bash", "sh").contains(normalized)) {
            return ToolOutput.builder().success(false).error("unsupported language: " + language).build();
        }

        List<String> command = switch (normalized) {
            case "python", "python3" -> List.of(resolvePythonExecutable(), "-c", code);
            case "bash" -> List.of("bash", "-lc", code);
            default -> List.of("sh", "-c", code);
        };

        try {
            ExecutorService processIoExecutor = OpenJiuwenExecutors.newFixedThreadPool("harness-code-process-io", 2,
                    true);
            try {
                Process process = new ProcessBuilder(command).start();
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                        () -> read(process.getInputStream()), processIoExecutor);
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                        () -> read(process.getErrorStream()), processIoExecutor);
                int exitCode = awaitProcessExit(process, command);
                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join();
                return buildCodeResult(exitCode, stdout, stderr);
            } finally {
                processIoExecutor.shutdownNow();
            }
        } catch (IOException | SecurityException | CompletionException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * Build the ToolOutput for a finished code execution.
     *
     * @param exitCode exitCode
     * @param stdout stdout
     * @param stderr stderr
     * @return the result
     * @since 0.1.7
     */
    private static ToolOutput buildCodeResult(int exitCode, String stdout, String stderr) {
        boolean isExecutionSuccessful = exitCode == 0;
        return ToolOutput.builder().success(isExecutionSuccessful)
                .data(Map.of("exit_code", exitCode, "stdout", stdout, "stderr", stderr))
                .error(isExecutionSuccessful
                        ? null
                        : (stderr.isBlank() ? "process exited with code " + exitCode : stderr))
                .build();
    }

    /**
     * Wait for the process to exit with the framework default process-join timeout.
     * On expiry, forcibly destroy the child and raise a recoverable SysOperationError.
     *
     * @param process the started child process
     * @param command the command line (for diagnostics)
     * @return the process exit code
     * @since 0.1.7
     */
    private static int awaitProcessExit(Process process, List<String> command) {
        // Issue #70 dim IV — bound process.onExit().join() with the framework default
        // process-join timeout; on expiry, destroyForcibly and raise a recoverable
        // SysOperationError. Mirrors BashTool treatment.
        long joinMs = TimeoutConstants.processJoinMs();
        try {
            return process.onExit()
                    .orTimeout(joinMs, TimeUnit.MILLISECONDS)
                    .join()
                    .exitValue();
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof TimeoutException) {
                Loggers.PERFORMANCE.warning(
                        "CodeTool process join timeout after {}ms, command='{}'",
                        joinMs, String.join(" ", command));
                process.destroyForcibly();
                throw new SysOperationError(
                        StatusCode.SYS_OPERATION_PROCESS_JOIN_TIMEOUT,
                        null, null, ce, Map.of(
                                "timeout", joinMs, "command", String.join(" ", command)));
            }
            throw ce;
        }
    }

    /**
     * resolvePythonExecutable
     *
     * @return the python cmd of the specific os
     * @since 0.1.7
     */
    private static String resolvePythonExecutable() {
        String override = System.getenv("PYTHON_EXECUTABLE");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] candidates = isWindows
                ? new String[] {"python", "python3", "python.exe"}
                : new String[] {"python3", "python"};
        for (String candidate : candidates) {
            if (isExecutableOnPath(candidate, isWindows)) {
                return candidate;
            }
        }
        return isWindows ? "python" : "python3";
    }

    /**
     * isExecutableOnPath.
     *
     * @param candidate executable name
     * @param isWindows whether the host OS is Windows
     * @return true when the executable exists on PATH
     * @since 0.1.7
     */
    private static boolean isExecutableOnPath(String candidate, boolean isWindows) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            java.io.File file = new java.io.File(dir, candidate);
            if (!file.isFile()) {
                continue;
            }
            if (isWindows) {
                return true;
            }
            if (file.canExecute()) {
                return true;
            }
            // Symlinks / non-exec bit files: Files.isExecutable follows links more reliably on Linux.
            try {
                if (java.nio.file.Files.isExecutable(file.toPath())) {
                    return true;
                }
            } catch (SecurityException ignored) {
                // keep scanning PATH
            }
        }
        return false;
    }

    /**
     * read.
     * 
     * @param stream stream
     * @return the result
     * @since 0.1.7
     */
    private static String read(InputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            stream.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
