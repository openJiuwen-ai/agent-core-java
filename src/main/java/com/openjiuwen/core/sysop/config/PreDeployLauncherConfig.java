/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * Configuration for pre-existing sandbox reachable via HTTP/WS.
 * <p>
 * Use this when the sandbox process is managed externally (e.g. already
 * running on a server or started by a sidecar). The launcher simply
 * returns the provided baseUrl without spinning up any process.
 * <p>
 * Mirrors Python's {@code PreDeployLauncherConfig} in {@code sys_operation/config.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PreDeployLauncherConfig extends SandboxLauncherConfig {

    /** Sandbox service base URL (http:// or ws://). Required. */
    private String baseUrl;

    /**
     * Create a PreDeployLauncherConfig with default launcher type.
     *
     * @param baseUrl    the sandbox service base URL
     * @param sandboxType the sandbox provider type
     * @return the config instance
     */
    public static PreDeployLauncherConfig create(String baseUrl, String sandboxType) {
        PreDeployLauncherConfig config = new PreDeployLauncherConfig();
        config.setBaseUrl(baseUrl);
        config.setLauncherType("pre_deploy");
        config.setSandboxType(sandboxType != null ? sandboxType : "aio");
        return config;
    }
}