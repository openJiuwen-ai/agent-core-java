/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

/**
 * Launcher for already-deployed sandbox services.
 * 
 * @since 0.1.7
 */
public class PreDeploymentLauncher implements SandboxLauncher {
    /**
     * launch.
     * 
     * @param config config
     * @param timeoutSeconds timeoutSeconds
     * @param isolationKey isolationKey
     * @return the result
     * @since 0.1.7
     */
    @Override
    public LaunchedSandbox launch(SandboxLauncherConfig config, int timeoutSeconds, String isolationKey) {
        if (config == null) {
            throw new IllegalArgumentException("PreDeploymentLauncher requires SandboxLauncherConfig");
        }

        String extraSandboxId = config.getExtraParams() != null
                ? (config.getExtraParams().get("sandbox_id") instanceof String s ? s : null)
                : null;

        String sandboxType = config.getSandboxType();
        String sandboxId;
        if ("jiuwenbox".equals(sandboxType) || "aio".equals(sandboxType)) {
            sandboxId = extraSandboxId != null && !extraSandboxId.isBlank() ? extraSandboxId : null;
        } else {
            sandboxId = extraSandboxId != null && !extraSandboxId.isBlank()
                    ? extraSandboxId
                    : (isolationKey != null && !isolationKey.isBlank() ? isolationKey : "pre_deploy");
        }

        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl()
                : config.getGatewayUrl();

        return LaunchedSandbox.builder().sandboxId(sandboxId).baseUrl(baseUrl != null ? baseUrl : "").build();
    }
}
