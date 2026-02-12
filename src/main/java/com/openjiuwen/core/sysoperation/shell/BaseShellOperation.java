// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.shell;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdResult;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdStreamResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Base shell command operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.shell.BaseShellOperation
 * 
 * <p>Defines the abstract interface for shell command operations:
 * <ul>
 *   <li>executeCmd - Execute shell command and return result</li>
 *   <li>executeCmdStream - Execute shell command with streaming output</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public abstract class BaseShellOperation extends BaseOperation {

    /**
     * Default timeout in seconds (5 minutes).
     */
    public static final int DEFAULT_TIMEOUT = 300;

    /**
     * Constructs a BaseShellOperation.
     */
    public BaseShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    /**
     * Asynchronously execute a shell command.
     * 
     * @param command Command to execute (required)
     * @param cwd Working directory for command execution (default: current directory)
     * @param timeout Command execution timeout in seconds (default: 300)
     * @param environment Custom environment variables
     * @param options Additional execution configuration options
     * @return CompletableFuture containing the execution result
     */
    public abstract CompletableFuture<ExecuteCmdResult> executeCmd(
        String command,
        String cwd,
        Integer timeout,
        Map<String, String> environment,
        Map<String, Object> options
    );

    /**
     * Default executeCmd with current directory and default timeout.
     */
    public CompletableFuture<ExecuteCmdResult> executeCmd(String command) {
        return executeCmd(command, null, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Execute command with specified working directory.
     */
    public CompletableFuture<ExecuteCmdResult> executeCmd(String command, String cwd) {
        return executeCmd(command, cwd, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Asynchronously execute a shell command with streaming output.
     * 
     * @param command Command to execute (required)
     * @param cwd Working directory for command execution (default: current directory)
     * @param timeout Command execution timeout in seconds (default: 300)
     * @param environment Custom environment variables
     * @param options Additional execution configuration options
     * @return Stream of execution chunk results
     */
    public Stream<ExecuteCmdStreamResult> executeCmdStream(
        String command,
        String cwd,
        Integer timeout,
        Map<String, String> environment,
        Map<String, Object> options
    ) {
        // Default implementation - subclasses may override
        throw new UnsupportedOperationException("executeCmdStream not implemented");
    }

    /**
     * Default executeCmdStream with current directory and default timeout.
     */
    public Stream<ExecuteCmdStreamResult> executeCmdStream(String command) {
        return executeCmdStream(command, null, DEFAULT_TIMEOUT, null, null);
    }
}

