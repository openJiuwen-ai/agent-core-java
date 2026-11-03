/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Launcher for pre-deployed sandbox services that already expose a base URL.
 * <p>
 * Mirrors Python's {@code PreDeploymentLauncher} in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/pre_deployment_launcher.py}.
 * </p>
 *
 * <p>Accepts {@link PreDeployLauncherConfig} or a plain {@link SandboxLauncherConfig}
 * that carries {@code base_url}/{@code gateway_url} (legacy compatibility). The
 * isolation key belongs to the gateway and is not a JiuwenBox or AIO sandbox
 * identifier. For those providers, an ID is returned only when explicitly
 * configured as {@code extra_params.sandbox_id}; unknown provider types retain
 * the legacy isolation-key fallback.</p>
 */
public final class PreDeploymentLauncher extends SandboxLauncher {

    @Override
    public CompletableFuture<LaunchedSandbox> launch(
            SandboxLauncherConfig config,
            int timeoutSeconds,
            String isolationKey) {
        String baseUrl = resolveBaseUrl(config)
                .orElseThrow(() -> new IllegalArgumentException("PreDeploymentLauncher requires base_url"));
        Optional<String> sandboxId = configuredSandboxId(config);
        if (sandboxId.isEmpty() && useIsolationKeyAsSandboxId(config)) {
            sandboxId = Optional.ofNullable(isolationKey).filter(key -> !key.isBlank());
        }
        if (sandboxId.isPresent()) {
            return CompletableFuture.completedFuture(new LaunchedSandbox(baseUrl, sandboxId.get()));
        }
        return CompletableFuture.completedFuture(new LaunchedSandbox(baseUrl));
    }

    private static Optional<String> configuredSandboxId(SandboxLauncherConfig config) {
        if (config == null || config.getExtraParams() == null) {
            return Optional.empty();
        }
        Object configuredId = config.getExtraParams().get("sandbox_id");
        if (configuredId instanceof String sandboxId && !sandboxId.isBlank()) {
            return Optional.of(sandboxId);
        }
        return Optional.empty();
    }

    private static boolean useIsolationKeyAsSandboxId(SandboxLauncherConfig config) {
        if (config == null) {
            return true;
        }
        String sandboxType = config.getSandboxType();
        return !"jiuwenbox".equalsIgnoreCase(sandboxType) && !"aio".equalsIgnoreCase(sandboxType);
    }

    private static Optional<String> resolveBaseUrl(SandboxLauncherConfig config) {
        if (config instanceof PreDeployLauncherConfig preDeployConfig) {
            Optional<String> preDeployBaseUrl = Optional.ofNullable(preDeployConfig.getBaseUrl())
                    .filter(baseUrl -> !baseUrl.isBlank());
            if (preDeployBaseUrl.isPresent()) {
                return preDeployBaseUrl;
            }
        }
        if (config == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.getBaseUrl())
                .filter(baseUrl -> !baseUrl.isBlank())
                .or(() -> Optional.ofNullable(config.getGatewayUrl()).filter(gatewayUrl -> !gatewayUrl.isBlank()));
    }
}
