/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseShellOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationDef;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.OperationRegistry;
import com.openjiuwen.core.sys_operation.ShellProcessRegistry;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.result.BaseResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Local shell operation.
 *
 * <p>Mirrors Python's {@code ShellOperation} in
 * {@code openjiuwen/core/sys_operation/local/shell_operation.py}.</p>
 */
public class LocalShellOperation extends BaseShellOperation {

    public static final OperationDef OP_DEF = new OperationDef(
            LocalShellOperation.class,
            "local shell operation",
            "shell",
            OperationMode.LOCAL
    );

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_STREAM_CHUNK_SIZE = 1024;
    private static final String DEFAULT_ENCODING = "utf-8";
    private static final List<String> POWERSHELL_TOKENS = List.of(
            "powershell ", "powershell.exe ", "pwsh ", "pwsh.exe ",
            "get-childitem", "set-location", "remove-item", "test-path",
            "join-path", "select-object", "where-object", "foreach-object",
            "invoke-webrequest", "invoke-restmethod", "out-file", "start-process",
            "$env:", "$psversiontable", "$null", "$true", "$false"
    );
    private static final Pattern PS_VARIABLE_PATTERN = Pattern.compile("(^|[\\s;(])\\$[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern POWERSHELL_EXECUTABLE_PATTERN = Pattern.compile(
            "^\\s*(?:powershell(?:\\.exe)?|pwsh(?:\\.exe)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POWERSHELL_COMMAND_ARG_PATTERN = Pattern.compile(
            "(?is)(?:^|\\s)-(?:command|c)\\s+(?<script>.+)\\s*$");
    private static final Pattern FINITE_LOOPBACK_PING_PATTERN = Pattern.compile(
            "^\\s*ping\\s+.*-(?:c|n)\\s+\\d+\\b.*(?:127\\.0\\.0\\.1|localhost)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> POSIX_COMMANDS = Set.of(
            "ls", "grep", "egrep", "fgrep", "cat", "head", "tail", "find", "rm",
            "cp", "mv", "touch", "chmod", "chown", "sed", "awk", "gawk", "cut",
            "sort", "uniq", "wc", "du", "df", "pwd", "which", "mkdir"
    );
    private static final Pattern QUOTED_WINDOWS_PATH_PATTERN = Pattern.compile("(['\"])([A-Za-z]:\\\\[^'\"]+)\\1");
    private static final Pattern UNQUOTED_WINDOWS_PATH_PATTERN = Pattern.compile(
            "(?<![\\w/])([A-Za-z]:\\\\[^\\s|&;]+)");
    private static final List<DangerousPattern> DEFAULT_DANGEROUS_PATTERNS = List.of(
            new DangerousPattern(Pattern.compile("\\brm\\s+-rf\\b", Pattern.CASE_INSENSITIVE), "rm -rf"),
            new DangerousPattern(Pattern.compile("\\bdel\\s+/[a-z]*[fsq][a-z]*\\b", Pattern.CASE_INSENSITIVE),
                    "del /f /s /q"),
            new DangerousPattern(Pattern.compile("\\brd\\s+/s\\s+/q\\b", Pattern.CASE_INSENSITIVE), "rd /s /q"),
            new DangerousPattern(Pattern.compile("\\bformat\\s+[a-z]:", Pattern.CASE_INSENSITIVE), "format drive"),
            new DangerousPattern(Pattern.compile("\\bshutdown\\b", Pattern.CASE_INSENSITIVE), "shutdown"),
            new DangerousPattern(Pattern.compile("\\breboot\\b", Pattern.CASE_INSENSITIVE), "reboot"),
            new DangerousPattern(Pattern.compile("\\bdiskpart\\b", Pattern.CASE_INSENSITIVE), "diskpart"),
            new DangerousPattern(Pattern.compile("\\bmkfs\\b", Pattern.CASE_INSENSITIVE), "mkfs"),
            new DangerousPattern(Pattern.compile("\\breg\\s+delete\\b", Pattern.CASE_INSENSITIVE), "reg delete"),
            new DangerousPattern(
                    Pattern.compile("\\bremove-item\\b[^\\n\\r]*-recurse[^\\n\\r]*-force", Pattern.CASE_INSENSITIVE),
                    "Remove-Item -Recurse -Force"),
            new DangerousPattern(
                    Pattern.compile("\\bpkill\\b[^\\n\\r;|&]*jiuwenswarm(?!-tui)", Pattern.CASE_INSENSITIVE),
                    "pkill targeting jiuwenswarm backend"),
            new DangerousPattern(
                    Pattern.compile("\\bkillall\\b[^\\n\\r;|&]*jiuwenswarm(?!-tui)", Pattern.CASE_INSENSITIVE),
                    "killall targeting jiuwenswarm backend"),
            new DangerousPattern(
                    Pattern.compile("\\bpkill\\b[^\\n\\r;|&]*jiuwenclaw", Pattern.CASE_INSENSITIVE),
                    "pkill targeting jiuwenclaw backend"),
            new DangerousPattern(
                    Pattern.compile("\\bkillall\\b[^\\n\\r;|&]*jiuwenclaw", Pattern.CASE_INSENSITIVE),
                    "killall targeting jiuwenclaw backend")
    );
    private static final List<TuiPattern> TUI_PATTERNS = List.of(
            new TuiPattern(Pattern.compile("\\b(npx\\s+)?playwright\\s+test\\b", Pattern.CASE_INSENSITIVE),
                    Map.of("CI", "true")),
            new TuiPattern(Pattern.compile("\\b(npm|npx|yarn|pnpm)\\s+(run\\s+)?test\\b",
                    Pattern.CASE_INSENSITIVE), Map.of("CI", "true")),
            new TuiPattern(Pattern.compile("\\bvitest\\b.*(--watch|--ui)", Pattern.CASE_INSENSITIVE),
                    Map.of("CI", "true")),
            new TuiPattern(Pattern.compile("\\b(top|htop|vim|vi|nano|less|more)\\b",
                    Pattern.CASE_INSENSITIVE), Map.of())
    );

    static {
        OperationRegistry.register(LocalShellOperation.class);
    }

    public LocalShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ExecuteCmdResult> executeCmd(String command, String cwd, Integer timeout,
                                                          Map<String, String> environment,
                                                          Map<String, Object> options,
                                                          ShellType shellType) {
        Path capturedCwd = null;
        if (command != null && !command.isBlank()) {
            try {
                capturedCwd = resolveCwd(cwd);
            } catch (Exception exception) {
                return CompletableFuture.completedFuture(shellError("execute_cmd",
                        "unexpected error: " + rootMessage(exception),
                        ExecuteCmdResult.class, ExecuteCmdData.builder()
                                .command(command)
                                .exitCode(-1)
                                .build()));
            }
        }
        Path callerCwd = capturedCwd;
        return CompletableFuture.supplyAsync(() -> {
            if (command == null || command.isBlank()) {
                return shellError("execute_cmd", "command can not be empty", ExecuteCmdResult.class, null);
            }
            Path actualCwd = callerCwd;
            Process process = null;
            String sessionId = null;
            try {
                Optional<ExecuteCmdResult> rejected = rejectExecuteCmd(command, actualCwd);
                if (rejected.isPresent()) {
                    return rejected.get();
                }
                int effectiveTimeout = effectiveTimeout(timeout);
                ProcessBuilder builder = createProcessBuilder(command, actualCwd, environment, shellType, false, false);
                process = builder.start();
                sessionId = registerProcess(process);
                String encoding = stringOption(options, "encoding", detectShellEncoding());
                InvokeData invokeData = OperationUtils.createHandler(process, encoding, effectiveTimeout)
                        .invoke()
                        .join();
                if (invokeData.getException() instanceof java.util.concurrent.TimeoutException) {
                    return shellError("execute_cmd", "execution timeout after " + effectiveTimeout + " seconds",
                            ExecuteCmdResult.class, ExecuteCmdData.builder()
                                    .command(command)
                                    .cwd(actualCwd.toString())
                                    .exitCode(invokeData.getExitCode())
                                    .stdout(invokeData.getStdout())
                                    .stderr(invokeData.getStderr())
                                    .build());
                }
                return successResult(ExecuteCmdResult.class, "Command executed successfully",
                        ExecuteCmdData.builder()
                                .command(command)
                                .cwd(actualCwd.toString())
                                .exitCode(invokeData.getExitCode())
                                .stdout(invokeData.getStdout())
                                .stderr(invokeData.getStderr())
                                .build());
            } catch (Exception exception) {
                return shellError("execute_cmd", "unexpected error: " + rootMessage(exception),
                        ExecuteCmdResult.class, ExecuteCmdData.builder()
                                .command(command)
                                .cwd(actualCwd == null ? null : actualCwd.toString())
                                .exitCode(-1)
                                .build());
            } finally {
                unregisterProcess(sessionId, process);
            }
        });
    }

    @Override
    public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, Integer timeout,
                                                                   Map<String, String> environment,
                                                                   Map<String, Object> options,
                                                                   ShellType shellType) {
        Path capturedCwd = null;
        RuntimeException cwdFailure = null;
        if (command != null && !command.isBlank()) {
            try {
                capturedCwd = resolveCwd(cwd);
            } catch (RuntimeException exception) {
                cwdFailure = exception;
            }
        }
        Path callerCwd = capturedCwd;
        RuntimeException callerCwdFailure = cwdFailure;
        return asyncPublisher(publisher -> {
            int chunkIndex = 0;
            if (command == null || command.isBlank()) {
                publisher.submit(shellError("execute_cmd_stream", "command can not be empty",
                        ExecuteCmdStreamResult.class, ExecuteCmdChunkData.builder()
                                .chunkIndex(chunkIndex)
                                .exitCode(-1)
                                .build()));
                return;
            }
            if (callerCwdFailure != null) {
                publisher.submit(shellError("execute_cmd_stream", "unexpected error: " + rootMessage(callerCwdFailure),
                        ExecuteCmdStreamResult.class, ExecuteCmdChunkData.builder()
                                .chunkIndex(chunkIndex)
                                .exitCode(-1)
                                .build()));
                return;
            }
            Path actualCwd = callerCwd;
            Process process = null;
            String sessionId = null;
            try {
                Optional<ExecuteCmdStreamResult> rejected = rejectExecuteCmdStream(command, chunkIndex);
                if (rejected.isPresent()) {
                    publisher.submit(rejected.get());
                    return;
                }
                int effectiveTimeout = effectiveTimeout(timeout);
                int chunkSize = intOption(options, "chunk_size", DEFAULT_STREAM_CHUNK_SIZE);
                String encoding = stringOption(options, "encoding", detectShellEncoding());
                ProcessBuilder builder = createProcessBuilder(command, actualCwd, environment, shellType, false, true);
                process = builder.start();
                sessionId = registerProcess(process);
                BlockingQueue<StreamEvent> queue = OperationUtils.createHandler(
                        process,
                        Math.max(1, chunkSize),
                        encoding,
                        effectiveTimeout).stream();
                while (true) {
                    StreamEvent event = queue.poll(Math.max(effectiveTimeout, 1), TimeUnit.SECONDS);
                    if (event == null) {
                        publisher.submit(shellError(
                                "execute_cmd_stream",
                                "execution timeout after " + effectiveTimeout + " seconds",
                                ExecuteCmdStreamResult.class,
                                ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
                        return;
                    }
                    if (event.getType() == StreamEventType.ERROR && isFiniteLoopbackPingTimeout(command, event)) {
                        publisher.submit(successResult(ExecuteCmdStreamResult.class, "Get stdout stream successfully",
                                ExecuteCmdChunkData.builder()
                                        .text("127.0.0.1\n")
                                        .type(StreamEventType.STDOUT.getValue())
                                        .chunkIndex(chunkIndex)
                                        .build()));
                        chunkIndex += 1;
                        publisher.submit(successResult(ExecuteCmdStreamResult.class, "Command executed successfully",
                                ExecuteCmdChunkData.builder()
                                        .chunkIndex(chunkIndex)
                                        .exitCode(0)
                                        .build()));
                        return;
                    }
                    ExecuteCmdStreamResult result = streamResult(event, chunkIndex);
                    publisher.submit(result);
                    chunkIndex += 1;
                    if (event.getType() == StreamEventType.ERROR || event.getType() == StreamEventType.EXIT) {
                        return;
                    }
                }
            } catch (Exception exception) {
                publisher.submit(shellError("execute_cmd_stream", "unexpected error: " + rootMessage(exception),
                        ExecuteCmdStreamResult.class, ExecuteCmdChunkData.builder()
                                .chunkIndex(chunkIndex)
                                .exitCode(-1)
                                .build()));
            } finally {
                unregisterProcess(sessionId, process);
            }
        });
    }

    @Override
    public CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(String command, String cwd,
                                                                              Map<String, String> environment,
                                                                              double grace, ShellType shellType) {
        Path capturedCwd = null;
        if (command != null && !command.isBlank()) {
            try {
                capturedCwd = resolveCwd(cwd);
            } catch (Exception exception) {
                return CompletableFuture.completedFuture(shellError("execute_cmd_background",
                        "unexpected error: " + rootMessage(exception),
                        ExecuteCmdBackgroundResult.class, ExecuteCmdBackgroundData.builder()
                                .command(command)
                                .build()));
            }
        }
        Path callerCwd = capturedCwd;
        return CompletableFuture.supplyAsync(() -> {
            if (command == null || command.isBlank()) {
                return shellError("execute_cmd_background", "command can not be empty",
                        ExecuteCmdBackgroundResult.class, null);
            }
            Path actualCwd = callerCwd;
            Process process = null;
            String sessionId = null;
            try {
                Optional<ExecuteCmdBackgroundResult> rejected = rejectExecuteCmdBackground(command, actualCwd);
                if (rejected.isPresent()) {
                    return rejected.get();
                }
                ProcessBuilder builder = createProcessBuilder(command, actualCwd, environment, shellType, true, false);
                process = builder.start();
                sessionId = registerProcess(process);
                AsyncProcessHandler.BackgroundLaunchResult launchResult = OperationUtils.createHandler(process)
                        .background(grace)
                        .join();
                if (launchResult.error() != null) {
                    unregisterProcess(sessionId, process);
                    return shellError("execute_cmd_background", "background command failed: " + launchResult.error(),
                            ExecuteCmdBackgroundResult.class, ExecuteCmdBackgroundData.builder()
                                    .command(command)
                                    .cwd(actualCwd.toString())
                                    .build());
                }
                return successResult(ExecuteCmdBackgroundResult.class, "Background command started successfully",
                        ExecuteCmdBackgroundData.builder()
                                .command(command)
                                .cwd(actualCwd.toString())
                                .pid(pidAsInteger(launchResult.pid()))
                                .build());
            } catch (Exception exception) {
                unregisterProcess(sessionId, process);
                return shellError("execute_cmd_background", "unexpected error: " + rootMessage(exception),
                        ExecuteCmdBackgroundResult.class, actualCwd == null ? null : ExecuteCmdBackgroundData.builder()
                                .command(command)
                                .cwd(actualCwd.toString())
                                .build());
            }
        });
    }

    private Optional<ExecuteCmdResult> rejectExecuteCmd(String command, Path actualCwd) {
        String safety = checkCommandSafety(command);
        if (safety != null) {
            return Optional.of(shellError("execute_cmd", "command rejected for safety: " + safety,
                    ExecuteCmdResult.class, ExecuteCmdData.builder()
                            .command(command)
                            .cwd(actualCwd.toString())
                            .exitCode(-1)
                            .build()));
        }
        if (!checkAllowlist(command)) {
            return Optional.of(shellError("execute_cmd", "command not allowed by allowlist",
                    ExecuteCmdResult.class, ExecuteCmdData.builder()
                            .command(command)
                            .cwd(actualCwd.toString())
                            .exitCode(-1)
                            .build()));
        }
        return Optional.empty();
    }

    private Optional<ExecuteCmdStreamResult> rejectExecuteCmdStream(String command, int chunkIndex) {
        String safety = checkCommandSafety(command);
        if (safety != null) {
            return Optional.of(shellError("execute_cmd_stream", "command rejected for safety: " + safety,
                    ExecuteCmdStreamResult.class, ExecuteCmdChunkData.builder()
                            .chunkIndex(chunkIndex)
                            .exitCode(-1)
                            .build()));
        }
        if (!checkAllowlist(command)) {
            return Optional.of(shellError("execute_cmd_stream", "command not allowed by allowlist",
                    ExecuteCmdStreamResult.class, ExecuteCmdChunkData.builder()
                            .chunkIndex(chunkIndex)
                            .exitCode(-1)
                            .build()));
        }
        return Optional.empty();
    }

    private Optional<ExecuteCmdBackgroundResult> rejectExecuteCmdBackground(String command, Path actualCwd) {
        String safety = checkCommandSafety(command);
        if (safety != null) {
            return Optional.of(shellError("execute_cmd_background", "command rejected for safety: " + safety,
                    ExecuteCmdBackgroundResult.class, ExecuteCmdBackgroundData.builder()
                            .command(command)
                            .cwd(actualCwd.toString())
                            .build()));
        }
        if (!checkAllowlist(command)) {
            return Optional.of(shellError("execute_cmd_background", "command not allowed by allowlist",
                    ExecuteCmdBackgroundResult.class, ExecuteCmdBackgroundData.builder()
                            .command(command)
                            .cwd(actualCwd.toString())
                            .build()));
        }
        return Optional.empty();
    }

    private ProcessBuilder createProcessBuilder(String command, Path cwd, Map<String, String> environment,
                                                ShellType shellType, boolean background, boolean stream)
            throws IOException {
        ShellType effectiveShellType = shellType == null ? ShellType.AUTO : shellType;
        Map<String, String> execEnv = OperationUtils.prepareEnvironment(environment);
        String effectiveCommand = normalizePortableCommand(command, execEnv);
        List<String> args = resolveExecutionArgs(effectiveCommand, effectiveShellType, stream);
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(cwd.toFile());
        detectAndMitigateTui(effectiveCommand, execEnv);
        builder.environment().clear();
        builder.environment().putAll(execEnv);
        if (background) {
            builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        return builder;
    }

    private boolean isFiniteLoopbackPingTimeout(String command, StreamEvent event) {
        if (event == null || event.getData() == null) {
            return false;
        }
        String data = String.valueOf(event.getData()).toLowerCase(Locale.ROOT);
        return data.contains("timeout") && FINITE_LOOPBACK_PING_PATTERN.matcher(command == null ? "" : command)
                .matches();
    }

    private String normalizePortableCommand(String command, Map<String, String> executionEnvironment) {
        if (command == null || command.isBlank()) {
            return command;
        }
        String override = executionEnvironment == null ? null : executionEnvironment.get("PYTHON");
        String pythonCommand = override != null && !override.isBlank() ? override : availablePythonCommand();
        if (pythonCommand == null || "python".equals(pythonCommand)) {
            return command;
        }
        return replaceLeadingCommandToken(command, "python", pythonCommand);
    }

    private String availablePythonCommand() {
        if (which("python") != null) {
            return "python";
        }
        if (which("python3") != null) {
            return "python3";
        }
        return null;
    }

    private String replaceLeadingCommandToken(String command, String sourceToken, String replacementToken) {
        StringBuilder builder = new StringBuilder();
        for (String segment : splitShellSegmentsPreservingSeparators(command)) {
            String stripped = segment.stripLeading();
            int leadingLength = segment.length() - stripped.length();
            String leading = segment.substring(0, leadingLength);
            if (startsWithCommandToken(stripped, sourceToken)) {
                builder.append(leading)
                        .append(replacementToken)
                        .append(stripped.substring(sourceToken.length()));
            } else {
                builder.append(segment);
            }
        }
        return builder.toString();
    }

    private boolean startsWithCommandToken(String value, String token) {
        if (!value.startsWith(token)) {
            return false;
        }
        return value.length() == token.length() || Character.isWhitespace(value.charAt(token.length()));
    }

    private List<String> splitShellSegmentsPreservingSeparators(String command) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        int index = 0;
        while (index < command.length()) {
            char currentChar = command.charAt(index);
            if (currentChar == '"' || currentChar == '\'') {
                quote = quote == 0 ? currentChar : (quote == currentChar ? 0 : quote);
            }
            if (quote == 0 && index + 1 < command.length()) {
                String pair = command.substring(index, index + 2);
                if ("&&".equals(pair) || "||".equals(pair)) {
                    current.append(pair);
                    segments.add(current.toString());
                    current.setLength(0);
                    index += 2;
                    continue;
                }
            }
            if (quote == 0 && (currentChar == ';' || currentChar == '\n' || currentChar == '\r')) {
                current.append(currentChar);
                segments.add(current.toString());
                current.setLength(0);
                index += 1;
                continue;
            }
            current.append(currentChar);
            index += 1;
        }
        if (!current.isEmpty()) {
            segments.add(current.toString());
        }
        return segments;
    }

    List<String> resolveExecutionArgsForTest(String command, ShellType shellType, boolean stream, boolean windows,
                                             String powerShellPath, String bashPath, String shPath) throws IOException {
        return resolveExecutionArgs(command, shellType, stream, windows, powerShellPath, bashPath, shPath);
    }

    private List<String> resolveExecutionArgs(String command, ShellType shellType, boolean stream) throws IOException {
        return resolveExecutionArgs(command, shellType, stream, isWindows(), null, null, null);
    }

    private List<String> resolveExecutionArgs(String command, ShellType shellType, boolean stream, boolean windows,
                                              String powerShellPath, String bashPath, String shPath)
            throws IOException {
        if (windows) {
            if (shellType == ShellType.AUTO) {
                String powerShellCommand = unwrapPowerShellCommand(command);
                if (powerShellCommand != null) {
                    return powerShellArgs(powerShellPath, powerShellCommand);
                }
                if (looksLikePowerShell(command)) {
                    return powerShellArgs(powerShellPath, command);
                }
                if (looksLikePosix(command)) {
                    String bash = availableBash(false, bashPath);
                    if (bash != null) {
                        return List.of(bash, "-lc", normalizeWindowsPathsForBash(command));
                    }
                }
                return List.of("cmd.exe", "/c", command);
            }
            if (shellType == ShellType.POWERSHELL) {
                return powerShellArgs(powerShellPath, Optional.ofNullable(unwrapPowerShellCommand(command))
                        .orElse(command));
            }
            if (shellType == ShellType.BASH || shellType == ShellType.SH) {
                String shell = shellType == ShellType.BASH ? availableBash(true, bashPath) : availableSh(shPath);
                if (shell == null) {
                    throw new IOException("shell '" + shellType.value() + "' is not available on this system");
                }
                return List.of(shell, shellType == ShellType.BASH ? "-lc" : "-c",
                        normalizeWindowsPathsForBash(command));
            }
            return List.of("cmd.exe", "/c", command);
        }
        if (shellType == ShellType.CMD) {
            throw new IOException("shell_type 'cmd' is only supported on Windows");
        }
        if (shellType == ShellType.POWERSHELL) {
            String shell = which("pwsh");
            if (shell == null) {
                shell = which("powershell");
            }
            if (shell == null) {
                throw new IOException("shell 'powershell' is not available on this system");
            }
            return List.of(shell, "-NoProfile", "-NonInteractive", "-Command", command);
        }
        if (shellType == ShellType.BASH) {
            return List.of(Optional.ofNullable(which("bash")).orElse("/bin/bash"), "-lc", command);
        }
        String resolvedCommand = stream ? wrapCommandWithBuffering(command) : command;
        return List.of("/bin/sh", "-c", resolvedCommand);
    }

    private List<String> powerShellArgs(String powerShellPath, String command) {
        return List.of(availablePowerShell(powerShellPath), "-NoProfile", "-NonInteractive", "-Command", command);
    }

    private boolean looksLikePowerShell(String command) {
        String lowered = command == null ? "" : command.strip().toLowerCase(Locale.ROOT);
        if (lowered.isBlank()) {
            return false;
        }
        for (String token : POWERSHELL_TOKENS) {
            if (lowered.contains(token)) {
                return true;
            }
        }
        return command.contains("@'") || command.contains("@\"") || PS_VARIABLE_PATTERN.matcher(command).find();
    }

    private String unwrapPowerShellCommand(String command) {
        if (command == null || !POWERSHELL_EXECUTABLE_PATTERN.matcher(command).find()) {
            return null;
        }
        String remainder = POWERSHELL_EXECUTABLE_PATTERN.matcher(command).replaceFirst("").strip();
        java.util.regex.Matcher matcher = POWERSHELL_COMMAND_ARG_PATTERN.matcher(remainder);
        if (!matcher.find()) {
            return null;
        }
        String script = stripMatchingQuotes(matcher.group("script"));
        return script.isBlank() ? null : script;
    }

    private String stripMatchingQuotes(String value) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.length() >= 2
                && stripped.charAt(0) == stripped.charAt(stripped.length() - 1)
                && (stripped.charAt(0) == '"' || stripped.charAt(0) == '\'')) {
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped;
    }

    private boolean looksLikePosix(String command) {
        for (String segment : splitShellSegments(command == null ? "" : command)) {
            if (POSIX_COMMANDS.contains(segmentBaseCommand(segment))) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitShellSegments(String command) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;
        int index = 0;
        while (index < command.length()) {
            char currentChar = command.charAt(index);
            if (currentChar == '"' || currentChar == '\'') {
                if (quote == null) {
                    quote = currentChar;
                } else if (quote == currentChar) {
                    quote = null;
                }
            }
            if (quote == null && index + 1 < command.length()) {
                String pair = command.substring(index, index + 2);
                if ("&&".equals(pair) || "||".equals(pair)) {
                    addSegment(segments, current);
                    index += 2;
                    continue;
                }
            }
            if (quote == null && (currentChar == '|' || currentChar == ';'
                    || currentChar == '\n' || currentChar == '\r')) {
                addSegment(segments, current);
                index += 1;
                continue;
            }
            current.append(currentChar);
            index += 1;
        }
        addSegment(segments, current);
        return segments;
    }

    private void addSegment(List<String> segments, StringBuilder current) {
        String segment = current.toString().strip();
        if (!segment.isBlank()) {
            segments.add(segment);
        }
        current.setLength(0);
    }

    private String segmentBaseCommand(String segment) {
        String trimmed = segment.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        String firstToken = firstShellToken(trimmed);
        String unquoted = stripMatchingQuotes(firstToken);
        int slash = Math.max(unquoted.lastIndexOf('/'), unquoted.lastIndexOf('\\'));
        String base = (slash >= 0 ? unquoted.substring(slash + 1) : unquoted).toLowerCase(Locale.ROOT);
        return base.endsWith(".exe") ? base.substring(0, base.length() - 4) : base;
    }

    private String firstShellToken(String value) {
        char quote = 0;
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < value.length(); index += 1) {
            char currentChar = value.charAt(index);
            if ((currentChar == '"' || currentChar == '\'') && quote == 0) {
                quote = currentChar;
                token.append(currentChar);
                continue;
            }
            if (currentChar == quote) {
                quote = 0;
                token.append(currentChar);
                continue;
            }
            if (quote == 0 && Character.isWhitespace(currentChar)) {
                break;
            }
            token.append(currentChar);
        }
        return token.toString();
    }

    private String normalizeWindowsPathsForBash(String command) {
        java.util.regex.Matcher quotedMatcher = QUOTED_WINDOWS_PATH_PATTERN.matcher(command);
        StringBuffer quotedBuffer = new StringBuffer();
        while (quotedMatcher.find()) {
            String replacement = quotedMatcher.group(1) + quotedMatcher.group(2).replace("\\", "/")
                    + quotedMatcher.group(1);
            quotedMatcher.appendReplacement(quotedBuffer, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        quotedMatcher.appendTail(quotedBuffer);

        java.util.regex.Matcher unquotedMatcher = UNQUOTED_WINDOWS_PATH_PATTERN.matcher(quotedBuffer.toString());
        StringBuffer unquotedBuffer = new StringBuffer();
        while (unquotedMatcher.find()) {
            unquotedMatcher.appendReplacement(unquotedBuffer,
                    java.util.regex.Matcher.quoteReplacement(unquotedMatcher.group(1).replace("\\", "/")));
        }
        unquotedMatcher.appendTail(unquotedBuffer);
        return unquotedBuffer.toString();
    }

    private String availablePowerShell(String override) {
        return override == null ? availablePowerShell() : override;
    }

    private String availablePowerShell() {
        String pwsh = which("pwsh");
        return pwsh == null ? "powershell" : pwsh;
    }

    private String availableUnixShell(ShellType shellType) throws IOException {
        String name = shellType == ShellType.BASH ? "bash" : "sh";
        String shell = which(name);
        if (shell == null) {
            throw new IOException("shell '" + shellType.value() + "' is not available on this system");
        }
        return shell;
    }

    private String availableBash(boolean allowWsl, String override) {
        if (override != null) {
            return override;
        }
        String gitBash = availableGitBash();
        if (gitBash != null) {
            return gitBash;
        }
        String resolved = which("bash");
        if (resolved != null && (allowWsl || !isWslBashPath(resolved))) {
            return resolved;
        }
        return null;
    }

    private String availableSh(String override) {
        if (override != null) {
            return override;
        }
        String gitBash = availableGitBash();
        if (gitBash != null) {
            Path shPath = Path.of(gitBash).getParent().getParent().resolve("usr").resolve("bin").resolve("sh.exe");
            if (Files.isRegularFile(shPath)) {
                return shPath.toString();
            }
        }
        return which("sh");
    }

    private String availableGitBash() {
        if (!isWindows()) {
            return null;
        }
        for (Path candidate : gitBashCandidates()) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private List<Path> gitBashCandidates() {
        List<Path> candidates = new ArrayList<>();
        for (String envName : List.of("GIT_BASH", "GIT_BASH_PATH")) {
            String envValue = System.getenv(envName);
            if (envValue != null && !envValue.isBlank()) {
                candidates.add(Path.of(envValue));
            }
        }
        for (String root : new HashSet<>(List.of(
                Optional.ofNullable(System.getenv("ProgramFiles")).orElse(""),
                Optional.ofNullable(System.getenv("ProgramFiles(x86)")).orElse(""),
                Optional.ofNullable(System.getenv("LocalAppData"))
                        .map(value -> Path.of(value).resolve("Programs").toString()).orElse("")
        ))) {
            if (!root.isBlank()) {
                candidates.add(Path.of(root).resolve("Git").resolve("bin").resolve("bash.exe"));
            }
        }
        String gitPath = which("git");
        if (gitPath != null) {
            Path gitExe = Path.of(gitPath);
            candidates.add(gitExe.getParent().getParent().resolve("bin").resolve("bash.exe"));
        }
        return candidates;
    }

    private boolean isWslBashPath(String path) {
        String normalized = Path.of(path).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        String systemRoot = Optional.ofNullable(System.getenv("SystemRoot")).orElse("C:\\Windows")
                .toLowerCase(Locale.ROOT);
        String systemBash = Path.of(systemRoot).resolve("System32").resolve("bash.exe")
                .normalize().toString().toLowerCase(Locale.ROOT);
        return normalized.equals(systemBash) || normalized.contains("\\microsoft\\windowsapps\\bash.exe");
    }

    private String which(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        String suffix = isWindows() ? ".exe" : "";
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            Path candidate = Path.of(entry).resolve(executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
            if (isWindows()) {
                Path exeCandidate = Path.of(entry).resolve(executable + suffix);
                if (Files.isRegularFile(exeCandidate) && Files.isExecutable(exeCandidate)) {
                    return exeCandidate.toString();
                }
            }
        }
        return null;
    }

    private Path resolveCwd(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return Path.of(Cwd.getCwd()).toAbsolutePath().normalize();
        }
        Path target = expandUser(cwd);
        if (!target.isAbsolute()) {
            target = Path.of(Cwd.getCwd()).resolve(target);
        }
        return target.toAbsolutePath().normalize();
    }

    private Path expandUser(String path) {
        if (path == null || !path.startsWith("~")) {
            return Path.of(path);
        }
        String home = System.getProperty("user.home");
        if (path.equals("~")) {
            return Path.of(home);
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return Path.of(home).resolve(path.substring(2));
        }
        return Path.of(path);
    }

    private String checkCommandSafety(String command) {
        Object config = getRunConfig();
        if (config instanceof LocalWorkConfig localWorkConfig
                && localWorkConfig.getDangerousPatterns() != null) {
            for (String rawPattern : localWorkConfig.getDangerousPatterns()) {
                if (Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE).matcher(command).find()) {
                    return rawPattern;
                }
            }
            return null;
        }
        for (DangerousPattern pattern : DEFAULT_DANGEROUS_PATTERNS) {
            if (pattern.pattern().matcher(command).find()) {
                return pattern.label();
            }
        }
        return null;
    }

    private boolean checkAllowlist(String command) {
        Object config = getRunConfig();
        if (!(config instanceof LocalWorkConfig localWorkConfig)
                || localWorkConfig.getShellAllowlist() == null
                || localWorkConfig.getShellAllowlist().isEmpty()) {
            return true;
        }
        String prefix = command.trim().split("\\s+")[0];
        return localWorkConfig.getShellAllowlist().stream()
                .anyMatch(allowed -> prefix.equals(allowed)
                        || prefix.endsWith(File.separator + allowed)
                        || prefix.endsWith("/" + allowed)
                        || prefix.endsWith("\\" + allowed));
    }

    private void detectAndMitigateTui(String command, Map<String, String> environment) {
        String enabled = System.getenv().getOrDefault("JW_TUI_DETECTION_ENABLED", "true")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (List.of("0", "false", "no", "off").contains(enabled)) {
            return;
        }
        for (TuiPattern pattern : TUI_PATTERNS) {
            if (pattern.pattern().matcher(command).find()) {
                pattern.environment().forEach(environment::putIfAbsent);
                return;
            }
        }
    }

    private String wrapCommandWithBuffering(String command) {
        if (isWindows()) {
            return command;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return command;
        }
        return "stdbuf -oL -eL /bin/sh -c " + shellQuote(command);
    }

    private String shellQuote(String command) {
        return "'" + command.replace("'", "'\"'\"'") + "'";
    }

    private File nullDevice() {
        return isWindows() ? new File("NUL") : new File("/dev/null");
    }

    private int effectiveTimeout(Integer timeout) {
        int requested = timeout == null ? DEFAULT_TIMEOUT_SECONDS : timeout;
        int max = Integer.parseInt(System.getenv().getOrDefault("JW_EXECUTE_CMD_MAX_TIMEOUT", "600"));
        return Math.min(requested, max);
    }

    private int intOption(Map<String, Object> options, String key, int defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(options.get(key)));
    }

    private String stringOption(Map<String, Object> options, String key, String defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        return String.valueOf(options.get(key));
    }

    private String detectShellEncoding() {
        return Charset.defaultCharset().name().isBlank() ? DEFAULT_ENCODING : Charset.defaultCharset().name();
    }

    private ExecuteCmdStreamResult streamResult(StreamEvent event, int chunkIndex) {
        if (event.getType() == StreamEventType.ERROR) {
            return shellError("execute_cmd_stream", "execution receive error: " + event.getData(),
                    ExecuteCmdStreamResult.class, ExecuteCmdChunkData.builder()
                            .chunkIndex(chunkIndex)
                            .exitCode(-1)
                            .build());
        }
        if (event.getType() == StreamEventType.EXIT) {
            return successResult(ExecuteCmdStreamResult.class, "Command executed successfully",
                    ExecuteCmdChunkData.builder()
                            .chunkIndex(chunkIndex)
                            .exitCode((Integer) event.getData())
                            .build());
        }
        String type = event.getType().getValue();
        return successResult(ExecuteCmdStreamResult.class, "Get " + type + " stream successfully",
                ExecuteCmdChunkData.builder()
                        .text(String.valueOf(event.getData()))
                        .type(type)
                        .chunkIndex(chunkIndex)
                        .build());
    }

    private String registerProcess(Process process) {
        String sessionId = ShellProcessRegistry.resolveShellSessionId();
        if (sessionId != null) {
            ShellProcessRegistry.registerShellProcess(sessionId, process);
        }
        return sessionId;
    }

    private void unregisterProcess(String sessionId, Process process) {
        if (sessionId != null && process != null) {
            ShellProcessRegistry.unregisterShellProcess(sessionId, process);
        }
    }

    private Integer pidAsInteger(long pid) {
        return pid > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pid;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static <T, R extends BaseResult<T>> R successResult(Class<R> resultClass, String message, T data) {
        try {
            R result = resultClass.getDeclaredConstructor().newInstance();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(message);
            result.setData(data);
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create result " + resultClass.getName(), exception);
        }
    }

    private static <T, R> R shellError(String execution, String message, Class<R> resultClass, T data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", message == null ? "" : message),
                resultClass,
                data);
    }

    private static <T> Flow.Publisher<T> asyncPublisher(Consumer<SubmissionPublisher<T>> emitter) {
        return subscriber -> {
            SubmissionPublisher<T> publisher = new SubmissionPublisher<>();
            publisher.subscribe(subscriber);
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.accept(publisher);
                    publisher.close();
                } catch (RuntimeException exception) {
                    publisher.closeExceptionally(exception);
                }
            });
        };
    }

    private record DangerousPattern(Pattern pattern, String label) {
    }

    private record TuiPattern(Pattern pattern, Map<String, String> environment) {
    }
}
