/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import com.openjiuwen.core.sysop.sandbox.gateway.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxStatus;

/**
 * Launcher for pre-existing sandbox reachable via HTTP/WS.
 * <p>
 * Use this when the sandbox process is managed externally (e.g. already
 * running on a server or started by a sidecar). The launcher simply
 * returns the provided baseUrl without spinning up any process.
 * <p>
 * Mirrors Python's {@code PreDeploymentLauncher} in {@code sandbox/launchers/pre_deployment_launcher.py}.
 */
public class PreDeploymentLauncher extends SandboxLauncher {

    @Override
    public LaunchedSandbox launch(Object config, int timeoutSeconds, String isolationKey) throws Exception {
        // Extract baseUrl from config (config is PreDeployLauncherConfig-like)
        String baseUrl = extractBaseUrl(config);
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("PreDeployLauncherConfig.baseUrl is required");
        }
        
        // Return a descriptor with the baseUrl, no sandboxId (externally managed)
        return LaunchedSandbox.builder()
                .baseUrl(baseUrl)
                .sandboxId(null) // External lifecycle, no tracking
                .hostPort(null)
                .build();
    }

    @Override
    public void pause(String sandboxId) throws Exception {
        // Pre-deployed sandboxes are externally managed, cannot pause
        throw new UnsupportedOperationException("PreDeploymentLauncher does not support pause");
    }

    @Override
    public void resume(String sandboxId) throws Exception {
        // Pre-deployed sandboxes are externally managed, cannot resume
        throw new UnsupportedOperationException("PreDeploymentLauncher does not support resume");
    }

    @Override
    public void delete(String sandboxId) throws Exception {
        // Pre-deployed sandboxes are externally managed, cannot delete
        // Silently ignore - external lifecycle
    }

    @Override
    public SandboxStatus checkStatus(String sandboxId) throws Exception {
        // Pre-deployed sandboxes are assumed always running (externally managed)
        return SandboxStatus.RUNNING;
    }

    /**
     * Extract baseUrl from config object.
     * <p>
     * Config is expected to have a baseUrl field (like PreDeployLauncherConfig).
     *
     * @param config the config object
     * @return the baseUrl string
     */
    private String extractBaseUrl(Object config) {
        if (config == null) {
            return null;
        }
        
        // Try reflection to get baseUrl field
        try {
            java.lang.reflect.Field field = config.getClass().getDeclaredField("baseUrl");
            field.setAccessible(true);
            return (String) field.get(config);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Try getter method
            try {
                java.lang.reflect.Method method = config.getClass().getMethod("getBaseUrl");
                return (String) method.invoke(config);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}