/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.base.BaseOperation;
import com.openjiuwen.core.sysop.base.OperationMode;
import com.openjiuwen.core.sysop.protocal.BaseShellProtocal;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local shell operation implementation.
 *
 * <p>Mirrors Python's {@code ShellOperation} in
 * {@code openjiuwen.core.sys_operation.local.shell_operation}.</p>
 */
public class ShellOperation extends BaseOperation implements BaseShellProtocal {

    /**
     * Create ShellOperation.
     *
     * @param runConfig run configuration
     */
    public ShellOperation(Object runConfig) {
        super("shell", OperationMode.LOCAL, "local shell operation", runConfig);
    }

    @Override
    public CompletableFuture<Object> executeCmd(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder();

                // Set command based on OS
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    pb.command("cmd.exe", "/c", command);
                } else {
                    pb.command("sh", "-c", command);
                }

                // Set working directory
                if (cwd != null) {
                    pb.directory(new java.io.File(cwd));
                }

                // Set environment
                if (environment != null) {
                    Map<String, String> env = pb.environment();
                    env.putAll(environment);
                }

                pb.redirectErrorStream(false);
                Process process = pb.start();

                // Read stdout
                StringBuilder stdout = new StringBuilder();
                BufferedReader stdoutReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = stdoutReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }

                // Read stderr
                StringBuilder stderr = new StringBuilder();
                BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));
                while ((line = stderrReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }

                // Wait for completion with timeout
                boolean finished = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);
                int exitCode = finished ? process.exitValue() : -1;

                if (!finished) {
                    process.destroyForcibly();
                }

                ExecuteCmdData data = ExecuteCmdData.builder()
                        .command(command)
                        .exitCode(exitCode)
                        .stdout(stdout.toString())
                        .stderr(stderr.toString())
                        .build();

                return ExecuteCmdResult.success(data);
            } catch (Exception e) {
                return ExecuteCmdResult.failure(e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Object> executeCmdStream(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        // Stream shell execution (same as regular for now)
        return executeCmd(command, cwd, timeout, environment, options);
    }

    @Override
    public List<ToolCard> listTools() {
        return List.of();
    }
}