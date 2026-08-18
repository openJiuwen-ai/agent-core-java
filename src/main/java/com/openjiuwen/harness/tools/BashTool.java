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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Public class BashTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class BashTool {
    private final String permissionMode;

    /**
     * BashTool.
     * 
     * @since 0.1.7
     */
    public BashTool() {
        this("auto");
    }

    /**
     * BashTool.
     * 
     * @param permissionMode permissionMode
     * @since 0.1.7
     */
    public BashTool(String permissionMode) {
        this.permissionMode = permissionMode != null ? permissionMode : "auto";
    }

    /**
     * invoke.
     * 
     * @param command command
     * @param workdir workdir
     * @param isRunInBackground isRunInBackground
     * @param maxOutputChars maxOutputChars
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(String command, String workdir, boolean isRunInBackground, Integer maxOutputChars) {
        if (command == null || command.isBlank()) {
            return ToolOutput.builder().success(false).error("command cannot be empty").build();
        }
        InjectionCheck injection = checkInjection(command);
        if (injection.isBlocked()) {
            return ToolOutput.builder().success(false).error(injection.reason()).build();
        }
        if ("read_only".equalsIgnoreCase(permissionMode) && looksLikeWrite(command)) {
            return ToolOutput.builder().success(false).error("Read-only mode blocks write command").build();
        }
        if (workdir != null && !workdir.isBlank() && !java.nio.file.Files.isDirectory(java.nio.file.Path.of(workdir))) {
            return ToolOutput.builder().success(false).error("workdir does not exist: " + workdir).build();
        }

        try {
            ProcessBuilder builder;
            if (isRunInBackground) {
                builder = new ProcessBuilder("bash", "-lc", command);
                if (workdir != null && !workdir.isBlank()) {
                    builder.directory(new java.io.File(workdir));
                }
                Process process = builder.start();
                return ToolOutput.builder().success(true).data(Map.of("pid", process.pid(), "status", "started"))
                        .build();
            }

            builder = new ProcessBuilder("bash", "-lc", command);
            if (workdir != null && !workdir.isBlank()) {
                builder.directory(new java.io.File(workdir));
            }
            ExecutorService processIoExecutor = OpenJiuwenExecutors.newFixedThreadPool("harness-bash-process-io", 2,
                    true);
            try {
                Process process = builder.start();
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                        () -> read(process.getInputStream()), processIoExecutor);
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                        () -> read(process.getErrorStream()), processIoExecutor);
                // Issue #70 dim IV — process.onExit().join() could block forever if the child
                // process deadlocked (e.g. waiting on a pipe whose reader died, or a REPL
                // never exiting). Bound it with the framework default process-join timeout;
                // on expiry, forcibly destroy the child and surface a recoverable
                // SysOperationError so the agent round can continue rather than hang.
                long joinMs = TimeoutConstants.processJoinMs();
                int exitCode;
                try {
                    exitCode = process.onExit()
                            .orTimeout(joinMs, TimeUnit.MILLISECONDS)
                            .join()
                            .exitValue();
                } catch (CompletionException ce) {
                    if (ce.getCause() instanceof TimeoutException) {
                        Loggers.PERFORMANCE.warning(
                                "BashTool process join timeout after {}ms, command='{}'",
                                joinMs, command);
                        process.destroyForcibly();
                        throw new SysOperationError(
                                StatusCode.SYS_OPERATION_PROCESS_JOIN_TIMEOUT,
                                null, null, ce, Map.of(
                                        "timeout", joinMs, "command", command));
                    }
                    throw ce;
                }
                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join();
                int limit = maxOutputChars != null ? Math.max(200, Math.min(maxOutputChars, 20000)) : 8000;
                boolean isExecutionSuccessful = exitCode == 0 || isNonErrorExit(command, exitCode);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("stdout", truncate(stdout, limit));
                payload.put("stderr", truncate(stderr, limit));
                payload.put("exit_code", exitCode);
                payload.put("return_code_interpretation", interpret(command, exitCode));
                payload.put("no_output_expected", isSilent(command));
                payload.put("destructive_warning", getDestructiveWarning(command));
                return ToolOutput.builder().success(isExecutionSuccessful).data(payload)
                        .error(isExecutionSuccessful
                                ? null
                                : truncate(stderr.isBlank() ? "command failed" : stderr, limit))
                        .build();
            } finally {
                processIoExecutor.shutdownNow();
            }
        } catch (IOException | SecurityException | CompletionException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * checkInjection.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    public static InjectionCheck checkInjection(String command) {
        if (command.contains("`")) {
            return new InjectionCheck(true, "injection isBlocked: backtick command substitution");
        }
        if (command.contains("$(")) {
            return new InjectionCheck(true, "injection isBlocked: $(...) command substitution");
        }
        if (command.contains("<(")) {
            return new InjectionCheck(true, "injection isBlocked: process substitution");
        }
        return new InjectionCheck(false, "");
    }

    /**
     * getDestructiveWarning.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    public static String getDestructiveWarning(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.contains("git reset --hard")) {
            return "This may discard uncommitted changes.";
        }
        if (lower.contains("git push --force") || lower.contains("git push -f")) {
            return "This may rewrite remote history.";
        }
        if (lower.contains("git commit --amend")) {
            return "This may rewrite commit history.";
        }
        if (lower.contains("drop table") || lower.contains("truncate table")) {
            return "This may destroy database data.";
        }
        if (lower.contains("terraform destroy")) {
            return "This may destroy Terraform-managed infrastructure.";
        }
        return null;
    }

    /**
     * looksLikeWrite.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    private static boolean looksLikeWrite(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        return List.of("touch ", "mkdir ", "rm ", "mv ", "cp ", "git commit", "git reset", "git clean", "echo ")
                .stream().anyMatch(lower::contains);
    }

    /**
     * isNonErrorExit.
     * 
     * @param command command
     * @param exitCode exitCode
     * @return the result
     * @since 0.1.7
     */
    private static boolean isNonErrorExit(String command, int exitCode) {
        return command.contains("grep") && exitCode == 1;
    }

    /**
     * interpret.
     * 
     * @param command command
     * @param exitCode exitCode
     * @return the result
     * @since 0.1.7
     */
    private static String interpret(String command, int exitCode) {
        if (command.contains("grep") && exitCode == 1) {
            return "No matches found";
        }
        return exitCode == 0 ? "Command completed successfully" : "Command failed";
    }

    /**
     * isSilent.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    private static boolean isSilent(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        return lower.startsWith("mkdir") || lower.startsWith("touch") || lower.startsWith("rm ");
    }

    /**
     * truncate.
     * 
     * @param text text
     * @param limit limit
     * @return the result
     * @since 0.1.7
     */
    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 32)) + "\n...[lines omitted]...";
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

    /**
     * Public record InjectionCheck used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record InjectionCheck(boolean isBlocked, String reason) {
    }
}
