/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.util.Iterator;
import java.util.Map;

/**
 * Sandbox shell operation stub — not yet implemented.
 * <p>
 * Mirrors Python's {@code sandbox/shell_operation.py}.
 */
@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation")
public class SandboxShellOperation extends BaseShellOperation {

    public SandboxShellOperation(Object runConfig) {
        super("shell", OperationMode.SANDBOX, "sandbox shell operation", runConfig);
    }

    @Override
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
                                       Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException("Shell operation sandbox mode is not implemented yet.");
    }

    @Override
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
                                                              Map<String, String> environment,
                                                              Map<String, Object> options) {
        throw new UnsupportedOperationException("Shell operation sandbox mode is not implemented yet.");
    }
}
