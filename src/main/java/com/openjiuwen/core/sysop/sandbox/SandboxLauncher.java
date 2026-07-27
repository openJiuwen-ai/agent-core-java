/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

/**
 * Lifecycle abstraction for sandbox acquisition and reclamation.
 */
public interface SandboxLauncher {
    LaunchedSandbox launch(SandboxLauncherConfig config, int timeoutSeconds, String isolationKey);

    default void pause(String sandboxId) {
    }

    default void resume(String sandboxId) {
    }

    default void delete(String sandboxId) {
    }

    default SandboxStatus checkStatus(String sandboxId) {
        return SandboxStatus.RUNNING;
    }
}
