/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.launchers;

import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxStatus;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code SandboxLauncher} in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/base.py}.
 */
public abstract class SandboxLauncher {

    public abstract CompletableFuture<LaunchedSandbox> launch(
            SandboxLauncherConfig config,
            int timeoutSeconds,
            String isolationKey);

    public CompletableFuture<LaunchedSandbox> launch(SandboxLauncherConfig config, int timeoutSeconds) {
        return launch(config, timeoutSeconds, null);
    }

    public CompletableFuture<Void> pause(String sandboxId) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> resume(String sandboxId) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> delete(String sandboxId) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<SandboxStatus> checkStatus(String sandboxId) {
        return CompletableFuture.completedFuture(SandboxStatus.RUNNING);
    }
}
