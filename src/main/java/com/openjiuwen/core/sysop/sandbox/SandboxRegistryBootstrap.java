/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

/**
 * Registers built-in launchers once per JVM.
 */
public final class SandboxRegistryBootstrap {
    private static volatile boolean isInitialized;

    private SandboxRegistryBootstrap() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void ensureInitialized() {
        if (isInitialized) {
            return;
        }
        synchronized (SandboxRegistryBootstrap.class) {
            if (isInitialized) {
                return;
            }
            SandboxRegistry.registerLauncher("pre_deploy", PreDeploymentLauncher.class);
            isInitialized = true;
        }
    }
}
