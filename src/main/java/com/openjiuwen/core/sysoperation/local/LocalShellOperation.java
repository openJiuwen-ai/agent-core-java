// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.registry.Operation;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdData;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdResult;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysoperation.shell.BaseShellOperation;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Local shell command operation implementation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.local.shell_operation.ShellOperation
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Operation(name = "shell", mode = OperationMode.LOCAL, description = "local shell operation")
public class LocalShellOperation extends BaseShellOperation {

    static {
        OperationRegistry.register(LocalShellOperation.class, "shell", OperationMode.LOCAL, "local shell operation");
    }

    public LocalShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ExecuteCmdResult> executeCmd(
            String command, String cwd, Integer timeout,
            Map<String, String> environment, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check allowlist
                if (!checkAllowlist(command)) {
                    return ExecuteCmdResult.failure(
                        StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(),
                        formatShellError("execute_cmd", "Command not allowed by allowlist")
                    );
                }

                // Resolve CWD
                Path actualCwd = resolveCwd(cwd);
                int actualTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;

                // Build process
                ProcessBuilder pb;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", command);
                } else {
                    pb = new ProcessBuilder("sh", "-c", command);
                }

                pb.directory(actualCwd.toFile());
                Map<String, String> env = pb.environment();
                if (environment != null) {
                    env.putAll(environment);
                }

                Process process = pb.start();

                // Read output
                StringBuilder stdout = new StringBuilder();
                StringBuilder stderr = new StringBuilder();

                String encoding = options != null && options.containsKey("encoding") 
                    ? (String) options.get("encoding") : "UTF-8";

                Thread stdoutThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), encoding))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stdout.append(line).append("\n");
                        }
                    } catch (Exception ignored) {}
                });

                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), encoding))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stderr.append(line).append("\n");
                        }
                    } catch (Exception ignored) {}
                });

                stdoutThread.start();
                stderrThread.start();

                // Wait with timeout
                boolean finished = process.waitFor(actualTimeout, TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    process.waitFor();
                    stdoutThread.join(1000);
                    stderrThread.join(1000);

                    String stdoutStr = stdout.toString();
                    String stderrStr = stderr.toString();
                    if (stdoutStr.endsWith("\n")) stdoutStr = stdoutStr.substring(0, stdoutStr.length() - 1);
                    if (stderrStr.endsWith("\n")) stderrStr = stderrStr.substring(0, stderrStr.length() - 1);

                    return new ExecuteCmdResult(
                        StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(),
                        formatShellError("execute_cmd", "Command timed out after " + actualTimeout + " seconds"),
                        ExecuteCmdData.builder()
                            .command(command)
                            .cwd(actualCwd.toString())
                            .exitCode(-1)
                            .stdout(stdoutStr)
                            .stderr(stderrStr)
                            .build()
                    );
                }

                stdoutThread.join(1000);
                stderrThread.join(1000);

                int exitCode = process.exitValue();
                String stdoutStr = stdout.toString();
                String stderrStr = stderr.toString();

                // Remove trailing newline
                if (stdoutStr.endsWith("\n")) {
                    stdoutStr = stdoutStr.substring(0, stdoutStr.length() - 1);
                }
                if (stderrStr.endsWith("\n")) {
                    stderrStr = stderrStr.substring(0, stderrStr.length() - 1);
                }

                ExecuteCmdData data = ExecuteCmdData.builder()
                    .command(command)
                    .cwd(actualCwd.toString())
                    .exitCode(exitCode)
                    .stdout(stdoutStr)
                    .stderr(stderrStr)
                    .build();

                return ExecuteCmdResult.success(data);

            } catch (Exception e) {
                return ExecuteCmdResult.failure(
                    StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(),
                    formatShellError("execute_cmd", e.getMessage())
                );
            }
        });
    }

    @Override
    public Stream<ExecuteCmdStreamResult> executeCmdStream(
            String command, String cwd, Integer timeout,
            Map<String, String> environment, Map<String, Object> options) {
        // Simplified: delegate to non-streaming version
        throw new UnsupportedOperationException("executeCmdStream not implemented");
    }

    private boolean checkAllowlist(String command) {
        LocalWorkConfig config = getLocalWorkConfig();
        if (config == null) return true;
        
        List<String> allowlist = config.getShellAllowlist();
        if (allowlist == null) return true;

        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) return false;
        
        String cmdPrefix = parts[0];
        String separator = File.separator;

        for (String allowed : allowlist) {
            if (cmdPrefix.equals(allowed) || cmdPrefix.endsWith(separator + allowed)) {
                return true;
            }
        }
        return false;
    }

    private Path resolveCwd(String cwd) {
        LocalWorkConfig config = getLocalWorkConfig();
        String workDirVal = config != null ? config.getWorkDir() : null;

        if (workDirVal == null) {
            if (cwd == null || cwd.isEmpty()) {
                return Paths.get("").toAbsolutePath();
            }
            return Paths.get(cwd).toAbsolutePath().normalize();
        }

        Path workDir = Paths.get(workDirVal).toAbsolutePath().normalize();
        if (cwd == null || cwd.isEmpty()) {
            return workDir;
        }

        Path target = Paths.get(cwd);
        if (!target.isAbsolute()) {
            target = workDir.resolve(target);
        }

        return target.toAbsolutePath().normalize();
    }

    private LocalWorkConfig getLocalWorkConfig() {
        Object config = getRunConfig();
        if (config instanceof LocalWorkConfig) {
            return (LocalWorkConfig) config;
        }
        return null;
    }

    private String formatShellError(String operation, String message) {
        return String.format("[sys_operation][%s] execution error: %s", operation, message);
    }
}

