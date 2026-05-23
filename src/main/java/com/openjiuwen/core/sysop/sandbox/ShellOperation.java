/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.base.BaseOperation;
import com.openjiuwen.core.sysop.base.OperationMode;
import com.openjiuwen.core.sysop.protocal.BaseShellProtocal;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sandbox shell operation implementation.
 *
 * <p>Mirrors Python's {@code ShellOperation} in
 * {@code openjiuwen.core.sys_operation.sandbox.shell_operation}.</p>
 */
public class ShellOperation extends BaseOperation implements BaseShellProtocal {

    /**
     * Create ShellOperation for sandbox.
     *
     * @param runConfig run configuration
     */
    public ShellOperation(Object runConfig) {
        super("shell", OperationMode.SANDBOX, "sandbox shell operation", runConfig);
    }

    @Override
    public CompletableFuture<Object> executeCmd(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        // Sandbox shell execution placeholder
        ExecuteCmdData data = ExecuteCmdData.builder()
                .command(command)
                .exitCode(0)
                .stdout("[Sandbox execution simulated]")
                .stderr("")
                .build();

        return CompletableFuture.completedFuture(ExecuteCmdResult.success(data));
    }

    @Override
    public CompletableFuture<Object> executeCmdStream(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        return executeCmd(command, cwd, timeout, environment, options);
    }

    @Override
    public List<ToolCard> listTools() {
        return List.of();
    }
}