// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.sandbox;

import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.registry.Operation;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdResult;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysoperation.shell.BaseShellOperation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Sandbox shell command operation placeholder.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.sandbox.shell_operation.ShellOperation
 * 
 * <p>Note: This is a placeholder implementation. Sandbox mode operations
 * are not yet implemented in the Java version.
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation")
public class SandboxShellOperation extends BaseShellOperation {

    static {
        OperationRegistry.register(SandboxShellOperation.class, "shell", OperationMode.SANDBOX, "sandbox shell operation");
    }

    public SandboxShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ExecuteCmdResult> executeCmd(
            String command, String cwd, Integer timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public Stream<ExecuteCmdStreamResult> executeCmdStream(
            String command, String cwd, Integer timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }
}

