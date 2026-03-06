/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.BaseResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Local shell command execution operation.
 * <p>
 * Mirrors Python's {@code ShellOperation} in {@code local/shell_operation.py}.
 */
@Operation(name = "shell", mode = OperationMode.LOCAL, description = "local shell operation")
public class LocalShellOperation extends BaseShellOperation {

    public LocalShellOperation(Object runConfig) {
        super("shell", OperationMode.LOCAL, "local shell operation", runConfig);
    }

    @Override
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
                                       Map<String, String> environment, Map<String, Object> options) {
        String methodName = "executeCmd";
        long startTime = System.currentTimeMillis();

        Loggers.SYS_OPERATION.info("Start to execute cmd");

        if (command == null || command.isBlank()) {
            return buildCmdErrorResult("command can not be empty", null);
        }

        Path actualCwd = null;
        try {
            actualCwd = resolveCwd(cwd);

            if (!checkAllowlist(command)) {
                return buildCmdErrorResult("command not allowed by allowlist",
                        ExecuteCmdData.builder().command(command).cwd(actualCwd.toString()).build());
            }

            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            String wrappedCommand = wrapCommandWithBuffering(command);

            ProcessBuilder pb = createShellProcessBuilder(wrappedCommand);
            pb.directory(actualCwd.toFile());
            pb.environment().putAll(env);
            Process process = pb.start();

            String encoding = options != null && options.containsKey("encoding")
                    ? (String) options.get("encoding") : "utf-8";
            Charset charset = Charset.forName(encoding);
            ProcessHandler handler = new ProcessHandler(process, 1024, charset, timeout);
            InvokeData invokeData = handler.invoke();

            if (invokeData.getException() instanceof InterruptedException) {
                return buildCmdErrorResult("execution timeout after " + timeout + " seconds",
                        ExecuteCmdData.builder()
                                .command(command).cwd(actualCwd.toString())
                                .exitCode(invokeData.getExitCode())
                                .stdout(invokeData.getStdout())
                                .stderr(invokeData.getStderr())
                                .build());
            }

            ExecuteCmdResult result = ExecuteCmdResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Command executed successfully")
                    .data(ExecuteCmdData.builder()
                            .command(command).cwd(actualCwd.toString())
                            .exitCode(invokeData.getExitCode())
                            .stdout(invokeData.getStdout())
                            .stderr(invokeData.getStderr())
                            .build())
                    .build();

            long elapsed = System.currentTimeMillis() - startTime;
            Loggers.SYS_OPERATION.info("End to execute cmd, elapsed={}ms", elapsed);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to execute cmd", e);
            return buildCmdErrorResult("unexpected error: " + e.getMessage(),
                    ExecuteCmdData.builder()
                            .command(command)
                            .cwd(actualCwd != null ? actualCwd.toString() : "")
                            .build());
        }
    }

    @Override
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
                                                              Map<String, String> environment,
                                                              Map<String, Object> options) {
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
            Path actualCwd = resolveCwd(cwd);

            if (!checkAllowlist(command)) {
                results.add(buildCmdStreamErrorResult("command not allowed by allowlist",
                        ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
                return results.iterator();
            }

            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            String wrappedCommand = wrapCommandWithBuffering(command);

            ProcessBuilder pb = createShellProcessBuilder(wrappedCommand);
            pb.directory(actualCwd.toFile());
            pb.environment().putAll(env);
            Process process = pb.start();

            int chunkSize = options != null && options.containsKey("chunk_size")
                    ? (Integer) options.get("chunk_size") : 1024;
            String encoding = options != null && options.containsKey("encoding")
                    ? (String) options.get("encoding") : "utf-8";
            Charset charset = Charset.forName(encoding);
            ProcessHandler handler = new ProcessHandler(process, chunkSize, charset, timeout);
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

    // --- Private helpers ---

    /**
     * Create a ProcessBuilder that runs a command through the system shell.
     */
    private ProcessBuilder createShellProcessBuilder(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            return new ProcessBuilder("/bin/sh", "-c", command);
        }
    }

    /**
     * Check if command first token is in allowlist.
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
        return allowlist.stream().anyMatch(allowed ->
                cmdPrefix.equals(allowed) || cmdPrefix.endsWith(separator + allowed));
    }

    /**
     * Resolve working directory, respecting LocalWorkConfig.workDir if set.
     */
    private Path resolveCwd(String cwd) {
        String workDirVal = null;
        if (getRunConfig() instanceof LocalWorkConfig config) {
            workDirVal = config.getWorkDir();
        }

        if (workDirVal == null) {
            if (cwd == null || cwd.isBlank()) {
                return Path.of("").toAbsolutePath();
            }
            return Path.of(cwd).toAbsolutePath();
        }

        Path workDir = Path.of(workDirVal).toAbsolutePath();
        if (cwd == null || cwd.isBlank()) {
            return workDir;
        }

        Path target = Path.of(cwd);
        if (!target.isAbsolute()) {
            target = workDir.resolve(target);
        }
        return target.toAbsolutePath().normalize();
    }

    /**
     * Wrap command with OS-specific buffering wrapper.
     */
    private String wrapCommandWithBuffering(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return command;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "script -q /dev/null " + command;
        } else {
            return "stdbuf -oL -eL " + command;
        }
    }

    private ExecuteCmdResult buildCmdErrorResult(String errorMsg, ExecuteCmdData data) {
        if (data != null && (data.getExitCode() == null || data.getExitCode() == 0)) {
            data.setExitCode(-1);
        }
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                "execute_cmd", errorMsg,
                ExecuteCmdResult::new, data);
    }

    private ExecuteCmdStreamResult buildCmdStreamErrorResult(String errorMsg, ExecuteCmdChunkData data) {
        if (data != null && (data.getExitCode() == null || data.getExitCode() == 0)) {
            data.setExitCode(-1);
        }
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                "execute_cmd_stream", errorMsg,
                ExecuteCmdStreamResult::new, data);
    }

    private ExecuteCmdStreamResult transformCmdStreamEvent(StreamEvent event, int chunkIndex) {
        return switch (event.getType()) {
            case STDOUT, STDERR -> {
                ExecuteCmdChunkData chunkData = ExecuteCmdChunkData.builder()
                        .text(event.getData())
                        .type(event.getType().getValue())
                        .chunkIndex(chunkIndex)
                        .build();
                yield ExecuteCmdStreamResult.builder()
                        .code(StatusCode.SUCCESS.getCode())
                        .message("Get " + chunkData.getType() + " stream successfully")
                        .data(chunkData)
                        .build();
            }
            case ERROR -> buildCmdStreamErrorResult("execution receive error: " + event.getData(),
                    ExecuteCmdChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build());
            case EXIT -> {
                int exitCode;
                try {
                    exitCode = Integer.parseInt(event.getData());
                } catch (NumberFormatException e) {
                    exitCode = -1;
                }
                ExecuteCmdChunkData chunkData = ExecuteCmdChunkData.builder()
                        .chunkIndex(chunkIndex)
                        .exitCode(exitCode)
                        .build();
                yield ExecuteCmdStreamResult.builder()
                        .code(StatusCode.SUCCESS.getCode())
                        .message("Command executed successfully")
                        .data(chunkData)
                        .build();
            }
        };
    }
}
