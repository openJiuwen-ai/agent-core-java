/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.launchers;

import com.openjiuwen.core.sys_operation.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Launcher for pre-deployed sandboxes that already expose a base URL.
 * <p>
 * Mirrors Python's {@code PreDeploymentLauncher} in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/pre_deployment_launcher.py}.
 */
public final class PreDeploymentLauncher extends SandboxLauncher {

    @Override
    public CompletableFuture<LaunchedSandbox> launch(
            SandboxLauncherConfig config,
            int timeoutSeconds,
            String isolationKey) {
        if (!(config instanceof PreDeployLauncherConfig preDeployConfig)) {
            throw new IllegalArgumentException("PreDeploymentLauncher requires PreDeployLauncherConfig");
        }
        return CompletableFuture.completedFuture(new LaunchedSandbox(preDeployConfig.getBaseUrl()));
    }
}
