/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

/**
 * Lifecycle abstraction for sandbox acquisition and reclamation.
 * 
 * @since 0.1.7
 */
public interface SandboxLauncher {
    /**
     * launch.
     * 
     * @param config config
     * @param timeoutSeconds timeoutSeconds
     * @param isolationKey isolationKey
     * @return the result
     * @since 0.1.7
     */
    LaunchedSandbox launch(SandboxLauncherConfig config, int timeoutSeconds, String isolationKey);

    /**
     * pause.
     * 
     * @param sandboxId sandboxId
     * @since 0.1.7
     */
    default void pause(String sandboxId) {
    }

    /**
     * resume.
     * 
     * @param sandboxId sandboxId
     * @since 0.1.7
     */
    default void resume(String sandboxId) {
    }

    /**
     * delete.
     * 
     * @param sandboxId sandboxId
     * @since 0.1.7
     */
    default void delete(String sandboxId) {
    }

    /**
     * checkStatus.
     * 
     * @param sandboxId sandboxId
     * @return the result
     * @since 0.1.7
     */
    default SandboxStatus checkStatus(String sandboxId) {
        return SandboxStatus.RUNNING;
    }
}
