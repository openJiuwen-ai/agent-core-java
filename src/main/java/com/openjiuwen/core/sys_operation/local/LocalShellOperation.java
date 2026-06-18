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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        List<String> args = resolveExecutionArgs(command, effectiveShellType, stream);
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(cwd.toFile());
        Map<String, String> execEnv = OperationUtils.prepareEnvironment(environment);
        detectAndMitigateTui(command, execEnv);
        builder.environment().clear();
        builder.environment().putAll(execEnv);
        if (background) {
            builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        return builder;
    }

    private List<String> resolveExecutionArgs(String command, ShellType shellType, boolean stream) throws IOException {
        boolean windows = isWindows();
        if (windows) {
            if (shellType == ShellType.POWERSHELL) {
                return List.of(availablePowerShell(), "-NoProfile", "-NonInteractive", "-Command", command);
            }
            if (shellType == ShellType.BASH || shellType == ShellType.SH) {
                String shell = availableUnixShell(shellType);
                return List.of(shell, shellType == ShellType.BASH ? "-lc" : "-c", command);
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
            return "script -q /dev/null /bin/sh -c " + shellQuote(command);
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
