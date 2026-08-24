/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Launcher for pre-deployed sandboxes that already expose a base URL.
 * <p>
 * Mirrors Python's {@code PreDeploymentLauncher} in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/pre_deployment_launcher.py}.
 * </p>
 *
 * <p>Accepts {@link PreDeployLauncherConfig} or a plain {@link SandboxLauncherConfig}
 * that carries {@code base_url}/{@code gateway_url} (legacy compatibility).</p>
 */
public final class PreDeploymentLauncher extends SandboxLauncher {

    @Override
    public CompletableFuture<LaunchedSandbox> launch(
            SandboxLauncherConfig config,
            int timeoutSeconds,
            String isolationKey) {
        String baseUrl = resolveBaseUrl(config);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("PreDeploymentLauncher requires base_url");
        }
        // Prefer isolation key as runtime sandbox id for ContainerManager / gateway clients.
        String sandboxId = (isolationKey != null && !isolationKey.isBlank()) ? isolationKey : null;
        return CompletableFuture.completedFuture(new LaunchedSandbox(baseUrl, sandboxId));
    }

    private static String resolveBaseUrl(SandboxLauncherConfig config) {
        if (config instanceof PreDeployLauncherConfig preDeployConfig) {
            String baseUrl = preDeployConfig.getBaseUrl();
            if (baseUrl != null && !baseUrl.isBlank()) {
                return baseUrl;
            }
        }
        if (config == null) {
            return null;
        }
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            return config.getBaseUrl();
        }
        if (config.getGatewayUrl() != null && !config.getGatewayUrl().isBlank()) {
            return config.getGatewayUrl();
        }
        return null;
    }
}
