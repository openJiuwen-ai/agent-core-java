/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.sysop.BaseOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;

import java.util.Map;

/**
 * Backward-compatible local shell operation for the legacy sysop package.
 *
 * <p>Mirrors Python's {@code ShellOperation} in
 * {@code openjiuwen/core/sys_operation/local/shell_operation.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.local.LocalShellOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class LocalShellOperation extends BaseOperation {

    private final com.openjiuwen.core.sys_operation.local.LocalShellOperation delegate;

    public LocalShellOperation(Object runConfig) {
        this("shell", OperationMode.LOCAL, "local shell operation", runConfig);
    }

    public LocalShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode == null ? com.openjiuwen.core.sys_operation.OperationMode.LOCAL : mode.toNewMode(),
                description, runConfig);
        this.delegate = new com.openjiuwen.core.sys_operation.local.LocalShellOperation(
                name,
                mode == null ? com.openjiuwen.core.sys_operation.OperationMode.LOCAL : mode.toNewMode(),
                description,
                runConfig);
    }

    public ExecuteCmdResult executeCmd(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options) {
        com.openjiuwen.core.sys_operation.result.ExecuteCmdResult result = delegate.executeCmd(
                command,
                cwd,
                timeout,
                environment,
                options,
                com.openjiuwen.core.sys_operation.BaseShellOperation.ShellType.AUTO).join();
        ExecuteCmdResult legacyResult = ExecuteCmdResult.fromNewResult(result);
        if (legacyResult != null && legacyResult.getData() != null) {
            legacyResult.getData().setShellType(
                    com.openjiuwen.core.sys_operation.BaseShellOperation.ShellType.AUTO.value());
        }
        return legacyResult;
    }
}
