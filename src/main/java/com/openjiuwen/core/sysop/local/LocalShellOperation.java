/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.ShellType;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.BaseResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Local shell command execution operation.
 * <p>
 * Mirrors Python's {@code ShellOperation} in {@code local/shell_operation.py}.
 * 
 * @since 0.1.7
 */
@Operation(name = "shell", mode = OperationMode.LOCAL, description = "local shell operation")
public class LocalShellOperation extends BaseShellOperation {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * LocalShellOperation.
     * 
     * @param runConfig runConfig
     * @since 0.1.7
     */
    public LocalShellOperation(Object runConfig) {
        super("shell", OperationMode.LOCAL, "local shell operation", runConfig);
    }

    /**
     * executeCmd.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        String methodName = "executeCmd";
        long startTime = System.currentTimeMillis();

        Loggers.SYS_OPERATION.info("Start to execute cmd");

        if (command == null || command.isBlank()) {
            return buildCmdErrorResult("command can not be empty", null);
        }

        Path actualCwd = null;
        try {
            int effectiveTimeout = normalizeTimeoutSeconds(timeout);
            actualCwd = resolveCwd(cwd);

            if (!checkAllowlist(command)) {
                return buildCmdErrorResult("command not allowed by allowlist",
                        ExecuteCmdData.builder().command(command).cwd(actualCwd.toString()).build());
            }
            String dangerousReason = checkDangerousPatterns(command);
            if (dangerousReason != null && !dangerousReason.isBlank()) {
                return buildCmdErrorResult(dangerousReason,
                        ExecuteCmdData.builder().command(command).cwd(actualCwd.toString()).build());
            }

            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            ShellType shellType = resolveShellType(options);
            String wrappedCommand = wrapCommandWithBuffering(command, shellType);

            ProcessBuilder pb = createShellProcessBuilder(wrappedCommand, shellType);
            pb.directory(actualCwd.toFile());
            pb.environment().putAll(env);
            Process process = pb.start();

            String encoding = options != null && options.get("encoding") instanceof String s ? s : "utf-8";
            Charset charset = Charset.forName(encoding);
            ProcessHandler handler = new ProcessHandler(process, 1024, charset, effectiveTimeout);
            InvokeData invokeData = handler.invoke();

            if (invokeData.getException() instanceof InterruptedException) {
                return buildCmdErrorResult("execution timeout after " + effectiveTimeout + " seconds",
                        ExecuteCmdData.builder().command(command).cwd(actualCwd.toString())
                                .exitCode(invokeData.getExitCode()).stdout(invokeData.getStdout())
                                .stderr(invokeData.getStderr()).build());
            }

            ExecuteCmdResult result =
                ExecuteCmdResult.builder().code(StatusCode.SUCCESS.getCode()).message("Command executed successfully")
                        .data(ExecuteCmdData.builder().command(command).cwd(actualCwd.toString())
                                .exitCode(invokeData.getExitCode()).stdout(invokeData.getStdout())
                                .stderr(invokeData.getStderr()).build())
                        .build();
            result.getData().setShellType(shellType.getValue());

            long elapsed = System.currentTimeMillis() - startTime;
            Loggers.SYS_OPERATION.info("End to execute cmd, elapsed={}ms", elapsed);
            return result;
        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to execute cmd", e);
            return buildCmdErrorResult("unexpected error: " + e.getMessage(), ExecuteCmdData.builder().command(command)
                    .cwd(actualCwd != null ? actualCwd.toString() : "").build());
        }
    }

    /**
     * executeCmdStream.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        String methodName = "executeCmdStream";
        long startTime = System.currentTimeMillis();
        List<ExecuteCmdStreamResult> results = new ArrayList<>();

        Loggers.SYS_OPERATION.info("Start to execute cmd streaming");

        int chunkIndex = 0;
        if (command == null || command.isBlank()) {
            results.add(buildCmdStreamErrorResult("command can not be empty",
                    ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
            return results.iterator();
        }

        try {
            int effectiveTimeout = normalizeTimeoutSeconds(timeout);

            if (!checkAllowlist(command)) {
                results.add(buildCmdStreamErrorResult("command not allowed by allowlist",
                        ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
                return results.iterator();
            }
            String dangerousReason = checkDangerousPatterns(command);
            if (dangerousReason != null && !dangerousReason.isBlank()) {
                results.add(buildCmdStreamErrorResult(dangerousReason,
                        ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
                return results.iterator();
            }

            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            ShellType shellType = resolveShellType(options);
            String wrappedCommand = wrapCommandWithBuffering(command, shellType);
            Path actualCwd = resolveCwd(cwd);

            ProcessBuilder pb = createShellProcessBuilder(wrappedCommand, shellType);
            pb.directory(actualCwd.toFile());
            pb.environment().putAll(env);
            Process process = pb.start();

            int chunkSize = options != null && options.get("chunk_size") instanceof Integer i ? i : 1024;
            String encoding = options != null && options.get("encoding") instanceof String s ? s : "utf-8";
            Charset charset = Charset.forName(encoding);
            ProcessHandler handler = new ProcessHandler(process, chunkSize, charset, effectiveTimeout);
            Iterator<StreamEvent> eventIterator = handler.stream();

            while (eventIterator.hasNext()) {
                StreamEvent event = eventIterator.next();
                ExecuteCmdStreamResult transformed = transformCmdStreamEvent(event, chunkIndex);
                if (transformed != null) {
                    results.add(transformed);
                    chunkIndex++;
                }
                if (event.getType() == StreamEventType.ERROR || event.getType() == StreamEventType.EXIT) {
                    break;
                }
            }

            return results.iterator();
        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to execute cmd streaming", e);
            results.add(buildCmdStreamErrorResult("unexpected error: " + e.getMessage(),
                    ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
            return results.iterator();
        }
    }

    /**
     * executeCmdBackground.
     * 
     * @param command command
     * @param cwd cwd
     * @param environment environment
     * @param grace grace
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd, Map<String, String> environment,
            double grace, Map<String, Object> options) {
        Loggers.SYS_OPERATION.info("Start to execute cmd background");

        if (command == null || command.isBlank()) {
            return buildCmdBackgroundErrorResult("command can not be empty", null);
        }

        Path actualCwd = null;
        try {
            actualCwd = resolveCwd(cwd);

            if (!checkAllowlist(command)) {
                return buildCmdBackgroundErrorResult("command not allowed by allowlist",
                        ExecuteCmdBackgroundData.builder().command(command).cwd(actualCwd.toString()).build());
            }
            String dangerousReason = checkDangerousPatterns(command);
            if (dangerousReason != null && !dangerousReason.isBlank()) {
                return buildCmdBackgroundErrorResult(dangerousReason,
                        ExecuteCmdBackgroundData.builder().command(command).cwd(actualCwd.toString()).build());
            }

            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            ShellType shellType = resolveShellType(options);
            ProcessBuilder pb = createShellProcessBuilder(command, shellType);
            pb.directory(actualCwd.toFile());
            pb.environment().putAll(env);
            Process process = pb.start();

            long pid = process.pid();
            if (grace > 0) {
                try {
                    Thread.sleep(graceMillis(grace));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    return buildCmdBackgroundErrorResult("background command exited early with code " + exitCode,
                            ExecuteCmdBackgroundData.builder().command(command).cwd(actualCwd.toString()).pid(pid)
                                    .build());
                }
            }

            return ExecuteCmdBackgroundResult.builder().code(StatusCode.SUCCESS.getCode())
                    .message("Background command started successfully")
                    .data(ExecuteCmdBackgroundData.builder().command(command).cwd(actualCwd.toString()).pid(pid)
                            .shellType(shellType.getValue()).build())
                    .build();
        } catch (IOException | RuntimeException e) {
            Loggers.SYS_OPERATION.error("Failed to execute cmd background", e);
            return buildCmdBackgroundErrorResult("unexpected error: " + e.getMessage(), ExecuteCmdBackgroundData
                    .builder().command(command).cwd(actualCwd != null ? actualCwd.toString() : "").build());
        }
    }

    // --- Private helpers ---

    /**
     * graceMillis.
     * 
     * @param grace grace
     * @return the result
     * @since 0.1.7
     */
    private static long graceMillis(double grace) {
        return Math.max(1L, BigDecimal.valueOf(grace).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValue());
    }

    /**
     * Create a ProcessBuilder that runs a command through the system shell.
     * 
     * @param command command
     * @param shellType shellType
     * @return the result
     * @since 0.1.7
     */
    private ProcessBuilder createShellProcessBuilder(String command, ShellType shellType) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return switch (normalizeShellType(shellType, os)) {
            case CMD -> new ProcessBuilder("cmd.exe", "/c", command);
            case POWERSHELL -> new ProcessBuilder("powershell", "-Command", command);
            case BASH -> new ProcessBuilder("bash", "-lc", command);
            case SH -> new ProcessBuilder("sh", "-c", command);
            default -> os.contains("win")
                    ? new ProcessBuilder("cmd.exe", "/c", command)
                    : new ProcessBuilder("/bin/sh", "-c", command);
        };
    }

    /**
     * normalizeTimeoutSeconds.
     * 
     * @param timeout timeout
     * @return the result
     * @since 0.1.7
     */
    private int normalizeTimeoutSeconds(int timeout) {
        return timeout > 0 ? timeout : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Check if command first token is in allowlist.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    private boolean checkAllowlist(String command) {
        if (!(getRunConfig() instanceof LocalWorkConfig config)) {
            return true;
        }
        List<String> allowlist = config.getShellAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return true;
        }

        String cmdPrefix = command.trim().split("\\s+")[0];
        String separator = File.separator;
        return allowlist.stream()
                .anyMatch(isAllowed -> cmdPrefix.equals(isAllowed) || cmdPrefix.endsWith(separator + isAllowed));
    }

    /**
     * checkDangerousPatterns.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    private String checkDangerousPatterns(String command) {
        if (!(getRunConfig() instanceof LocalWorkConfig config)) {
            return null;
        }
        List<String> dangerousPatterns = config.getDangerousPatterns();
        if (dangerousPatterns == null || dangerousPatterns.isEmpty()) {
            return null;
        }
        for (String rawPattern : dangerousPatterns) {
            if (rawPattern == null || rawPattern.isBlank()) {
                continue;
            }
            if (Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE).matcher(command).find()) {
                return "command blocked by dangerous pattern: " + rawPattern;
            }
        }
        return null;
    }

    /**
     * Resolve working directory, respecting LocalWorkConfig.workDir if set.
     * 
     * @param cwd cwd
     * @return the result
     * @since 0.1.7
     */
    private Path resolveCwd(String cwd) {
        LocalWorkConfig config = getRunConfig() instanceof LocalWorkConfig localConfig ? localConfig : null;
        String workDirVal = config != null ? config.getWorkDir() : null;
        Path baseDir =
            workDirVal != null ? Path.of(workDirVal).toAbsolutePath() : Path.of(CwdContext.getCwd()).toAbsolutePath();

        if (workDirVal == null) {
            if (cwd == null || cwd.isBlank()) {
                return baseDir;
            }
            Path raw = Path.of(cwd);
            return raw.isAbsolute() ? raw.toAbsolutePath() : baseDir.resolve(raw).toAbsolutePath();
        }

        Path workDir = baseDir;
        if (cwd == null || cwd.isBlank()) {
            return workDir;
        }

        Path target = Path.of(cwd);
        if (!target.isAbsolute()) {
            target = workDir.resolve(target);
        }
        Path normalized = target.toAbsolutePath().normalize();

        if (config != null && config.isRestrictToSandbox()) {
            List<Path> sandboxRoots = new ArrayList<>();
            if (config.getSandboxRoot() != null && !config.getSandboxRoot().isEmpty()) {
                for (String root : config.getSandboxRoot()) {
                    if (root != null && !root.isBlank()) {
                        sandboxRoots.add(Path.of(root).toAbsolutePath().normalize());
                    }
                }
            } else {
                if (CwdContext.getWorkspace() != null) {
                    sandboxRoots.add(Path.of(CwdContext.getWorkspace()).toAbsolutePath().normalize());
                }
                sandboxRoots.add(Path.of(CwdContext.getProjectRoot()).toAbsolutePath().normalize());
            }

            boolean isAllowed = sandboxRoots.stream().anyMatch(root -> {
                try {
                    return !root.relativize(normalized).startsWith("..");
                } catch (IllegalArgumentException ex) {
                    return false;
                }
            });
            if (!isAllowed) {
                throw new IllegalArgumentException(
                        "Access denied: cwd " + normalized + " traverses outside " + sandboxRoots);
            }
        }

        return normalized;
    }

    /**
     * Wrap command with OS-specific buffering wrapper.
     * 
     * @param command command
     * @param shellType shellType
     * @return the result
     * @since 0.1.7
     */
    private String wrapCommandWithBuffering(String command, ShellType shellType) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ShellType normalized = normalizeShellType(shellType, os);
        if (os.contains("win") || normalized == ShellType.CMD || normalized == ShellType.POWERSHELL) {
            return command;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "script -q /dev/null " + command;
        } else {
            return "stdbuf -oL -eL " + command;
        }
    }

    /**
     * resolveShellType.
     * 
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private ShellType resolveShellType(Map<String, Object> options) {
        if (options == null) {
            return ShellType.AUTO;
        }
        Object shellType = options.get("shell_type");
        return shellType == null ? ShellType.AUTO : ShellType.fromString(String.valueOf(shellType));
    }

    /**
     * normalizeShellType.
     * 
     * @param shellType shellType
     * @param os os
     * @return the result
     * @since 0.1.7
     */
    private ShellType normalizeShellType(ShellType shellType, String os) {
        ShellType normalized = shellType == null ? ShellType.AUTO : shellType;
        if (os.contains("win")) {
            return normalized;
        }
        if (normalized == ShellType.CMD || normalized == ShellType.POWERSHELL) {
            return ShellType.AUTO;
        }
        return normalized;
    }

    /**
     * buildCmdErrorResult.
     * 
     * @param errorMsg errorMsg
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCmdResult buildCmdErrorResult(String errorMsg, ExecuteCmdData data) {
        if (data != null && (data.getExitCode() == null || data.getExitCode() == 0)) {
            data.setExitCode(-1);
        }
        return BaseResult.buildOperationErrorResult(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR, "execute_cmd",
                errorMsg, ExecuteCmdResult::new, data);
    }

    /**
     * buildCmdStreamErrorResult.
     * 
     * @param errorMsg errorMsg
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCmdStreamResult buildCmdStreamErrorResult(String errorMsg, ExecuteCmdChunkData data) {
        if (data != null && (data.getExitCode() == null || data.getExitCode() == 0)) {
            data.setExitCode(-1);
        }
        return BaseResult.buildOperationErrorResult(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                "execute_cmd_stream", errorMsg, ExecuteCmdStreamResult::new, data);
    }

    /**
     * buildCmdBackgroundErrorResult.
     * 
     * @param errorMsg errorMsg
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCmdBackgroundResult buildCmdBackgroundErrorResult(String errorMsg, ExecuteCmdBackgroundData data) {
        return BaseResult.buildOperationErrorResult(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                "execute_cmd_background", errorMsg, ExecuteCmdBackgroundResult::new, data);
    }

    /**
     * transformCmdStreamEvent.
     * 
     * @param event event
     * @param chunkIndex chunkIndex
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCmdStreamResult transformCmdStreamEvent(StreamEvent event, int chunkIndex) {
        return switch (event.getType()) {
            case STDOUT, STDERR -> {
                ExecuteCmdChunkData chunkData = ExecuteCmdChunkData.builder().text(event.getDataAsString())
                        .type(event.getType().getValue()).chunkIndex(chunkIndex).build();
                yield ExecuteCmdStreamResult.builder().code(StatusCode.SUCCESS.getCode())
                        .message("Get " + chunkData.getType() + " stream successfully").data(chunkData).build();
            }
            case ERROR -> buildCmdStreamErrorResult("execution receive error: " + event.getDataAsString(),
                    ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build());
            case EXIT -> {
                Integer exitCode = event.getDataAsInt();
                if (exitCode == null) {
                    exitCode = -1;
                }
                ExecuteCmdChunkData chunkData =
                    ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(exitCode).build();
                yield ExecuteCmdStreamResult.builder().code(StatusCode.SUCCESS.getCode())
                        .message("Command executed successfully").data(chunkData).build();
            }
        };
    }
}
