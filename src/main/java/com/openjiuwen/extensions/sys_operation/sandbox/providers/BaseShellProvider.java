/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

/**
 * Base interface for shell operations.
 */
public interface BaseShellProvider {

    /**
     * Execute a shell command.
     *
     * @param command Command to execute.
     * @param workingDir Working directory.
     * @return CompletableFuture with execution result.
     */
    java.util.concurrent.CompletableFuture<AioProvider.ExecuteResult> executeCommand(String command, String workingDir);
}