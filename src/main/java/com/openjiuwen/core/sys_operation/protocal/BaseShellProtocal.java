/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.protocal;

import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Unified shell method signatures shared by operation and provider layers.
 *
 * <p>Mirrors Python's {@code BaseShellProtocal} in
 * {@code openjiuwen/core/sys_operation/protocal/shell_protocal.py}.</p>
 */
public abstract class BaseShellProtocal {

    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public abstract CompletableFuture<ExecuteCmdResult> executeCmd(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment,
            Map<String, Object> options);

    public abstract Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment,
            Map<String, Object> options);

    public CompletableFuture<ExecuteCmdResult> executeCmd(String command) {
        return executeCmd(command, null, DEFAULT_TIMEOUT_SECONDS, null, null);
    }

    public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(String command) {
        return executeCmdStream(command, null, DEFAULT_TIMEOUT_SECONDS, null, null);
    }
}
