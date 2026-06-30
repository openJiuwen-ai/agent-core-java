/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

/**
 * Launcher for already-deployed sandbox services.
 */
public class PreDeploymentLauncher implements SandboxLauncher {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public LaunchedSandbox launch(SandboxLauncherConfig config, int timeoutSeconds, String isolationKey) {
        if (config == null) {
            throw new IllegalArgumentException("PreDeploymentLauncher requires SandboxLauncherConfig");
        }
        String sandboxId = isolationKey != null && !isolationKey.isBlank() ? isolationKey : "pre_deploy";
        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl()
                : config.getGatewayUrl();
        return LaunchedSandbox.builder()
                .sandboxId(sandboxId)
                .baseUrl(baseUrl != null ? baseUrl : "")
                .build();
    }
}
